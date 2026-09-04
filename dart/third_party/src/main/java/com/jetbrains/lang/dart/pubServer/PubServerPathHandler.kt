// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.lang.dart.pubServer

import com.google.common.net.UrlEscapers
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.lang.dart.logging.PluginLogger
import com.jetbrains.lang.dart.util.DartUrlResolver
import com.jetbrains.lang.dart.util.PubspecYamlUtil
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaders
import org.jetbrains.builtInWebServer.WebServerPathHandler

private val LOG = PluginLogger.createLogger(PubServerPathHandler::class.java)

private class PubServerPathHandler : WebServerPathHandler {
  override fun process(
    path: String,
    project: Project,
    request: FullHttpRequest,
    context: ChannelHandlerContext,
    projectName: String,
    authHeaders: HttpHeaders,
    isCustomHost: Boolean,
  ): Boolean {
    val servedDirAndPathForPubServer = ReadAction.nonBlocking<Pair<VirtualFile, String>?> {
      getServedDirAndPathForPubServer(project, path, projectName)
    }.executeSynchronously() ?: return false
    PubServerManager.getInstance(project).send(context.channel(), request, authHeaders, servedDirAndPathForPubServer.first,
                                               servedDirAndPathForPubServer.second)
    return true
  }
}

private fun findFileInContentRoots(project: Project, path: String): VirtualFile? {
  val relativePath = path.trimStart('/')
  for (root in ProjectRootManager.getInstance(project).contentRoots) {
    ProgressManager.checkCanceled()
    val file = root.findFileByRelativePath(relativePath)
    if (file != null) {
      return file
    }
  }
  return null
}

private fun removeProjectNamePrefix(path: String, projectName: String?): String {
  if (projectName.isNullOrEmpty()) return path
  val trimmed = path.removePrefix("/")
  return when {
    trimmed == projectName -> ""
    trimmed.startsWith("$projectName/") -> trimmed.removePrefix(projectName)
    else -> path
  }
}

fun getServedDirAndPathForPubServer(
  project: Project,
  path: String,
  projectName: String? = null,
): Pair<VirtualFile, String>? {
  val effectivePath = removeProjectNamePrefix(path, projectName)

  // File with requested path may not exist, pub server will generate and serve it.
  // Here we find deepest (if nested) Dart project (aka Dart package) folder and its existing subfolder that can be served by pub server.

  // There may be 2 content roots with web/foo.html and web/bar.html files in them correspondingly. We need to catch the correct 'web' folder.
  // First see if full path can be resolved to a file
  val file = findFileInContentRoots(project, effectivePath)
  if (file != null && ProjectFileIndex.getInstance(project).isInContent(file)) {
    return getServedDirAndPathForPubServer(project, file)
  }

  // If above failed then take the longest path part that corresponds to an existing folder
  var servedDir: VirtualFile? = null
  var pubServePath: String? = null

  var slashIndex = -1
  while (true) {
    ProgressManager.checkCanceled()
    slashIndex = effectivePath.indexOf('/', slashIndex + 1)
    if (slashIndex < 0) {
      break
    }

    val pathPart = effectivePath.substring(0, slashIndex)
    val dir = findFileInContentRoots(project, pathPart)
    if (dir == null || !dir.isDirectory) {
      continue
    }

    val parentDir = dir.parent
    if (parentDir?.findChild(PubspecYamlUtil.PUBSPEC_YAML) != null) {
      val name = dir.nameSequence
      if (StringUtil.equals(name, "build") || StringUtil.equals(name, "lib") || StringUtil.equals(name, DartUrlResolver.PACKAGES_FOLDER_NAME)) {
        // contents of "build" folder should be served by the IDE internal web server directly, i.e. without pub serve
        return null
      }

      servedDir = dir
      pubServePath = effectivePath.substring(slashIndex)
      // continue looking for nested Dart project
    }
  }

  return servedDir?.let { Pair(it, escapeUrl(pubServePath!!)) }
}

fun getServedDirAndPathForPubServer(project: Project, file: VirtualFile): Pair<VirtualFile, String>? {
  val dartRoot = PubspecYamlUtil.findPubspecYamlFile(project, file)?.parent ?: return null
  val relativePath = VfsUtilCore.getRelativePath(file, dartRoot)
  // we only handle files 2 levels deeper than the Dart project root
  val slashIndex = relativePath?.indexOf('/') ?: -1
  val folderName = if (slashIndex == -1) null else relativePath!!.substring(0, slashIndex)
  if (folderName == null || folderName == "build" || folderName == "lib" || DartUrlResolver.PACKAGES_FOLDER_NAME == folderName) {
    return null
  }

  val pubServePath = relativePath!!.substring(slashIndex)
  return Pair(dartRoot.findChild(folderName), escapeUrl(pubServePath))
}

private fun escapeUrl(path: String): String {
  try {
    // need to restore slash separators after UrlEscapers.urlPathSegmentEscaper work
    return StringUtil.replace(UrlEscapers.urlPathSegmentEscaper().escape(path), "%2F", "/")
  }
  catch (e: Exception) {
    LOG.warn(path, e)
    return path
  }
}
