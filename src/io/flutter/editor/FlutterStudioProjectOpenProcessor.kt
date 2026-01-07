/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.ui.EditorNotifications
import io.flutter.FlutterUtils
import io.flutter.project.FlutterProjectOpenProcessor
import io.flutter.pub.PubRoot
import io.flutter.utils.FlutterModuleUtils

/**
 * Originally `FlutterStudioProjectOpenProcessor.java`.
 *
 * This processor is specific to Android Studio (or when the Flutter Studio plugin is active).
 * It extends `FlutterProjectOpenProcessor` to provide specialized handling for Android Studio,
 * particularly ensuring that the project is correctly re-detected if the opening process causes a reload.
 *
 * Converted to Kotlin to support `openProjectAsync`.
 */
open class FlutterStudioProjectOpenProcessor : FlutterProjectOpenProcessor() {
  override val name: String
    get() = "Flutter Studio"

  override fun canOpenProject(file: VirtualFile): Boolean =
    PubRoot.forDirectory(file)?.declaresFlutter() == true

  /**
   * Replaces the deprecated `doOpenProject`.
   *
   * Performs the same logic as the original Java implementation but using `suspend` and `writeAction`.
   *
   * Key differences from the base class:
   * - Explicitly looks up the project again using `FlutterUtils.findProject` after opening, as the project instance might have changed
   *   (e.g. if the platform closed and reopened it during import).
   * - Ensures Dart SDK is enabled and notifications are updated.
   */
  override suspend fun openProjectAsync(
    virtualFile: VirtualFile,
    projectToClose: Project?,
    forceOpenInNewFrame: Boolean,
  ): Project? {
    val importProvider = getDelegateImportProvider(virtualFile) ?: return null
    val project = importProvider.openProjectAsync(virtualFile, projectToClose, forceOpenInNewFrame)
    
    // A callback may have caused the project to be reloaded. Find the new Project object.
    var newProject = findProject(virtualFile.path)
    if (newProject == null || newProject.isDisposed) {
      // Fallback to the original project if re-lookup fails.
      if (project != null && !project.isDisposed) {
        newProject = project
      } else {
        return null
      }
    }

    configureFlutterProject(newProject)
    
    return newProject
  }

  @org.jetbrains.annotations.VisibleForTesting
  protected open suspend fun configureFlutterProject(project: Project) {
    for (module in FlutterModuleUtils.getModules(project)) {
      if (FlutterModuleUtils.declaresFlutter(module) && !FlutterModuleUtils.isFlutterModule(module)) {
        writeAction {
          FlutterModuleUtils.setFlutterModuleType(module)
        }
        FlutterModuleUtils.enableDartSDK(module)
      }
    }
    project.save()
    EditorNotifications.getInstance(project).updateAllNotifications()
  }

  @org.jetbrains.annotations.VisibleForTesting
  protected open fun findProject(path: String): Project? {
    return FlutterUtils.findProject(path)
  }

  override fun doOpenProject(
    virtualFile: VirtualFile,
    projectToClose: Project?,
    forceOpenInNewFrame: Boolean,
  ): Project? {
    return null
  }
}
