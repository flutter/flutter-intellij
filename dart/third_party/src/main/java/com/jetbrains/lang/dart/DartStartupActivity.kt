// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.lang.dart

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.impl.ModifiableModelCommitter
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService
import com.jetbrains.lang.dart.ide.toolingDaemon.DartToolingDaemonService
import com.jetbrains.lang.dart.projectWizard.DartModuleBuilder
import com.jetbrains.lang.dart.sdk.DartSdk
import com.jetbrains.lang.dart.analytics.Analytics
import com.jetbrains.lang.dart.analytics.SettingsData
import com.jetbrains.lang.dart.sdk.DartConfigurable
import com.jetbrains.lang.dart.sdk.DartSdkLibUtil
import com.jetbrains.lang.dart.util.PubspecYamlUtil
import kotlinx.coroutines.launch

/**
 * [DartStartupActivity] configures "Dart Packages" library (based on Dart-specific pubspec.yaml and .packages files) on a project open.
 * Afterward the "Dart Packages" library is kept up-to-dated thanks to [DartFileListener] and [DartWorkspaceModelChangeListener].
 *
 * @see DartFileListener
 * @see DartWorkspaceModelChangeListener
 */
class DartStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    val serviceScope = DartAnalysisServerService.getInstance(project).serviceScope

    serviceScope.launch {
      // Group all required exclusions by module and content root in the background to avoid EDT lockup
      // See: https://github.com/flutter/dart-intellij-third-party/issues/149
      val exclusionsByModule = readAction {
        val exclusions = mutableMapOf<Module, MutableMap<VirtualFile, MutableSet<String>>>()
        val exclusionsCache = mutableMapOf<Module, Set<String>>()
        val fileIndex = ProjectFileIndex.getInstance(project)
        val pubspecYamlFiles = FilenameIndex.getVirtualFilesByName(PubspecYamlUtil.PUBSPEC_YAML, GlobalSearchScope.projectScope(project))
        for (file in pubspecYamlFiles) {
          // Allow the platform to cancel this background scanning task if the user starts typing
          ProgressManager.checkCanceled()
          val module = ModuleUtilCore.findModuleForFile(file, project) ?: continue
          val root = file.parent ?: continue
          val contentRoot = fileIndex.getContentRootForFile(root) ?: continue
          val rootUrl = root.url

          // Cache already-excluded roots per module to prevent redundant lookups in monorepos
          val existingExclusions = exclusionsCache.getOrPut(module) {
            module.rootManager.excludeRootUrls.toSet()
          }

          val urlsToExclude = getExclusionUrls(rootUrl) - existingExclusions
          if (urlsToExclude.isNotEmpty()) {
            exclusions.getOrPut(module) { mutableMapOf() }
              .getOrPut(contentRoot) { mutableSetOf() }
              .addAll(urlsToExclude)
          }
        }
        exclusions
      }

      // Apply and commit all changes in a single EDT write action transaction to prevent UI freezes
      if (exclusionsByModule.isNotEmpty()) {
        edtWriteAction {
          val modelsToCommit = mutableListOf<ModifiableRootModel>()
          try {
            for ((module, contentRootToUrls) in exclusionsByModule) {
              if (module.isDisposed) continue
              val model = module.rootManager.getModifiableModel()
              var changed = false
              for ((contentRoot, urls) in contentRootToUrls) {
                model.contentEntries.find { it.file == contentRoot }?.let { entry ->
                  for (url in urls) {
                    entry.addExcludeFolder(url)
                    changed = true
                  }
                }
              }
              if (changed) {
                modelsToCommit.add(model)
              } else {
                model.dispose() // Dispose immediately if no changes occurred for this module
              }
            }

            // Commit all models together in a single global rootsChanged transaction
            if (modelsToCommit.isNotEmpty()) {
              val moduleModel = project.moduleManager.getModifiableModel()
              var committed = false
              try {
                ModifiableModelCommitter.multiCommit(modelsToCommit, moduleModel)
                committed = true
              } finally {
                if (!committed) {
                  moduleModel.dispose()
                }
              }
            }
          } finally {
            // Guarantee cleanup of any uncommitted models to prevent memory/resource leaks
            modelsToCommit.forEach { model ->
              if (!model.isDisposed) {
                model.dispose()
              }
            }
          }
        }
        DartFileListener.scheduleDartPackageRootsUpdate(project)
      }
    }

    serviceScope.launch {
      startAnalysisServerIfNeeded(project)
    }

    serviceScope.launch {
      reportSettingsAnalytics(project)
    }
  }

  private suspend fun startAnalysisServerIfNeeded(project: Project) {
    if (DartModuleBuilder.isPubGetScheduledForNewlyCreatedProject(project)) {
      // We want to start Analysis Server after the initial 'pub get' is finished, this will be done in DartPubActionBase
      return
    }

    if (DartSdk.getDartSdk(project) == null) return
    if (project.moduleManager.modules.find { DartSdkLibUtil.isDartSdkEnabled(it) } == null) return

    readActionBlocking {
      DartAnalysisServerService.getInstance(project).serverReadyForRequest()
    }

    if (Registry.`is`("dart.launch.dtd.and.devtools", false)) {
      DartToolingDaemonService.getInstance(project).startService()
    }
  }

  private suspend fun reportSettingsAnalytics(project: Project) {
    val info = readAction {
      val sdk = DartSdk.getDartSdk(project)
      val enabled = sdk != null && project.moduleManager.modules.any { DartSdkLibUtil.isDartSdkEnabled(it) }
      val version = sdk?.version ?: "unknown"
      val experimentalEnabled = DartConfigurable.isExperimentalLspFeaturesEnabled(project)
      SettingsReportInfo(sdk, enabled, version, experimentalEnabled)
    }

    if (!info.dartSupportEnabled || info.sdk == null) return

    val config = Analytics.getConfiguration(info.sdk, project)
    if (config.suppressAnalytics) return

    val settingsData = SettingsData(project)
    settingsData["experimentalLspFeaturesEnabled"] = info.experimentalLspFeaturesEnabled
    settingsData["sdkVersion"] = info.sdkVersion

    Analytics.report(settingsData)
  }
}

private data class SettingsReportInfo(
  val sdk: DartSdk?,
  val dartSupportEnabled: Boolean,
  val sdkVersion: String,
  val experimentalLspFeaturesEnabled: Boolean
)

fun excludeBuildAndToolCacheFolders(module: Module, pubspecYamlFile: VirtualFile) {
  prepareExcludeBuildAndToolCacheFolders(module, pubspecYamlFile)?.invoke()
}

private fun prepareExcludeBuildAndToolCacheFolders(module: Module, pubspecYamlFile: VirtualFile): (() -> Unit)? {
  val root = pubspecYamlFile.parent ?: return null
  val contentRoot = ProjectFileIndex.getInstance(module.project).getContentRootForFile(root) ?: return null
  val rootUrl = root.url

  val urlsToExclude = getExclusionUrls(rootUrl) -
    module.rootManager.excludeRootUrls.toSet()
  if (urlsToExclude.isEmpty()) return null

  return {
    ModuleRootModificationUtil.updateExcludedFolders(module, contentRoot, emptyList(), urlsToExclude)
  }
}

private fun getExclusionUrls(rootUrl: String): Set<String> =
  setOf("$rootUrl/.dart_tool", "$rootUrl/.pub", "$rootUrl/build")

private val Module.rootManager: ModuleRootManager
  get() = ModuleRootManager.getInstance(this)

private val Project.moduleManager: ModuleManager
  get() = ModuleManager.getInstance(this)
