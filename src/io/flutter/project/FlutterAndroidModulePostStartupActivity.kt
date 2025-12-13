/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project

import com.android.tools.idea.npw.importing.SourceToGradleModuleStep
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import io.flutter.module.FlutterModuleBuilder
import io.flutter.utils.FlutterModuleUtils
import io.flutter.utils.OpenApiUtils
import java.io.File

class FlutterAndroidModulePostStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (ApplicationManager.getApplication().isUnitTestMode) return

    DumbService.getInstance(project).smartInvokeLater {
      addAndroidModuleIfNeeded(project)
    }
  }

  private fun addAndroidModuleIfNeeded(project: Project) {
    val modules = FlutterModuleUtils.getModules(project)
    for (module in modules) {
      if (!FlutterModuleUtils.declaresFlutter(module)) continue

      val roots = OpenApiUtils.getContentRoots(module)
      if (roots.isEmpty() || roots.size > 1) continue

      val root = roots[0]
      val moduleName = module.name

      // Check if Android module logic applies
      if (!projectHasContentRoot(project, root)) continue

      val imlName = "${moduleName}_android.iml"
      var moduleDir: File? = null

      val rootFile = File(root.path)
      if (File(rootFile, imlName).exists()) {
        moduleDir = rootFile
      } else {
        for (name in arrayOf("android", ".android")) {
          val dir = File(rootFile, name)
          if (dir.exists() && File(dir, imlName).exists()) {
            moduleDir = dir
            break
          }
        }
      }

      if (moduleDir != null) {
        try {
          FlutterModuleBuilder.addAndroidModule(project, null, moduleDir.path, module.name, true)
        } catch (ignored: IllegalStateException) {
        }
      }

      // Check for example module
      val example = File(rootFile, "example")
      if (example.exists()) {
        val android = File(example, "android")
        val exampleFile = File(android, "${moduleName}_example_android.iml")
        if (android.exists() && exampleFile.exists()) {
          try {
            val virtualExampleFile = LocalFileSystem.getInstance().findFileByIoFile(exampleFile)
            if (virtualExampleFile != null) {
              FlutterModuleBuilder.addAndroidModuleFromFile(project, null, virtualExampleFile)
            }
          } catch (ignored: IllegalStateException) {
          }
        }
      }
    }
  }

  private fun projectHasContentRoot(project: Project, root: VirtualFile): Boolean {
    for (module in FlutterModuleUtils.getModules(project)) {
      for (file in OpenApiUtils.getContentRoots(module)) {
        if (root == file) return true
      }
    }
    return false
  }

  companion object {
    private val LOG = Logger.getInstance(FlutterAndroidModulePostStartupActivity::class.java)
  }
}
