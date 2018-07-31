/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.ui.EditorNotifications;
import io.flutter.FlutterUtils;
import io.flutter.pub.PubRoot;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterStudioProjectOpenProcessor extends FlutterProjectOpenProcessor {
  @Override
  public String getName() {
    return "Flutter Studio";
  }

  @Override
  public boolean canOpenProject(@Nullable VirtualFile file) {
    if (file == null) return false;
    final PubRoot root = PubRoot.forDirectory(file);
    return root != null && root.declaresFlutter();
  }

  @Nullable
  @Override
  public Project doOpenProject(@NotNull VirtualFile virtualFile, @Nullable Project projectToClose, boolean forceOpenInNewFrame) {
    final ProjectOpenProcessor importProvider = getDelegateImportProvider(virtualFile);
    if (importProvider == null) return null;

    importProvider.doOpenProject(virtualFile, projectToClose, forceOpenInNewFrame);
    // A callback may have caused the project to be reloaded. Find the new Project object.
    Project project = FlutterUtils.findProject(virtualFile.getPath());
    if (project == null || project.isDisposed()) {
      return project;
    }
    for (Module module : FlutterModuleUtils.getModules(project)) {
      if (FlutterModuleUtils.declaresFlutter(module) && !FlutterModuleUtils.isFlutterModule(module)) {
        FlutterModuleUtils.setFlutterModuleType(module);
        FlutterModuleUtils.enableDartSDK(module);
      }
    }
    project.save();
    EditorNotifications.getInstance(project).updateAllNotifications();

    FlutterProjectCreator.disableUserConfig(project);
    return project;
  }
}
