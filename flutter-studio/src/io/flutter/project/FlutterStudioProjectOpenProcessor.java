/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterUtils;
import io.flutter.pub.PubRoot;
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
    ApplicationInfo info = ApplicationInfo.getInstance();
    final PubRoot root = PubRoot.forDirectory(file);
    return root != null && root.declaresFlutter();
  }

  @Nullable
  @Override
  public Project doOpenProject(@NotNull VirtualFile virtualFile, @Nullable Project projectToClose, boolean forceOpenInNewFrame) {
    if (super.doOpenProject(virtualFile, projectToClose, forceOpenInNewFrame) == null) return null;
    // The superclass may have caused the project to be reloaded. Find the new Project object.
    Project project = FlutterUtils.findProject(virtualFile.getPath());
    if (project != null) {
      FlutterProjectCreator.disableUserConfig(project);
      return project;
    }
    return null;
  }
}
