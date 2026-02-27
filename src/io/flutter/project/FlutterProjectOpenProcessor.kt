/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor
import icons.FlutterIcons
import io.flutter.FlutterBundle
import io.flutter.FlutterUtils
import io.flutter.pub.PubRoot
import io.flutter.utils.FlutterModuleUtils
import java.util.*
import javax.swing.Icon

/**
 * Originally `FlutterProjectOpenProcessor.java`.
 *
 * This processor handles opening Flutter projects when they are selected directly (e.g. via "Open" in the IDE).
 * It delegates the actual opening to the platform's default processor (e.g. Gradle or Maven processor if applicable,
 * or the generic project opener) and then ensures that any modules in the project are correctly configured as Flutter modules.
 *
 * Converted to Kotlin to support `openProjectAsync` which is a suspend function.
 */
open class FlutterProjectOpenProcessor : ProjectOpenProcessor() {
  override val name: String
    get() = FlutterBundle.message("flutter.module.name")

  override fun getIcon(file: VirtualFile): Icon? {
    return FlutterIcons.Flutter
  }

  override fun canOpenProject(file: VirtualFile): Boolean {
    if (FlutterUtils.isAndroidStudio()) {
      return false
    }
    val root = PubRoot.forDirectory(file)
    return root != null && root.declaresFlutter()
  }

  /**
   * Replaces the deprecated `doOpenProject`.
   *
   * This method is `suspend` and must be used instead of `doOpenProject` to avoid `IllegalStateException` in newer IDE versions.
   *
   * It performs the following steps:
   * 1. Finds a delegate processor (e.g. Gradle) to open the project.
   * 2. Opens the project asynchronously.
   * 3. Once opened, checks if the project contains Flutter modules that are not yet configured as such (e.g. missing module type).
   * 4. Configures these modules as Flutter modules within a write action.
   */
  override suspend fun openProjectAsync(
    virtualFile: VirtualFile,
    projectToClose: Project?,
    forceOpenInNewFrame: Boolean,
  ): Project? {
    // Delegate opening to the platform open processor.
    val importProvider = getDelegateImportProvider(virtualFile) ?: return null
    val project = importProvider.openProjectAsync(virtualFile, projectToClose, forceOpenInNewFrame)
    if (project == null || project.isDisposed) return project

    // Convert any modules that use Flutter but don't have IntelliJ Flutter metadata.
    convertToFlutterProject(project)

    return project
  }

  /**
   * Deprecated method, kept to satisfy the compiler/interface.
   *
   * We return `null` to indicate that this processor does not support the synchronous opening method
   * and that `openProjectAsync` should be used instead.
   */
  override fun doOpenProject(
    virtualFile: VirtualFile,
    projectToClose: Project?,
    forceOpenInNewFrame: Boolean,
  ): Project? {
    return null
  }

  protected open fun getDelegateImportProvider(file: VirtualFile): ProjectOpenProcessor? {
    return EXTENSION_POINT_NAME.extensionList.stream().filter { processor: ProjectOpenProcessor ->
      processor.canOpenProject(file) && !Objects.equals(
        processor.name,
        name
      )
    }.findFirst().orElse(null)
  }
}

/**
 * Sets up a project that doesn't have any Flutter modules.
 *
 *
 * (It probably wasn't created with "flutter create" and probably didn't have any IntelliJ configuration before.)
 */
private fun convertToFlutterProject(project: Project) {
  for (module in FlutterModuleUtils.getModules(project)) {
    if (FlutterModuleUtils.declaresFlutter(module) && !FlutterModuleUtils.isFlutterModule(module)) {
      FlutterModuleUtils.setFlutterModuleAndReload(module, project)
    }
  }
}