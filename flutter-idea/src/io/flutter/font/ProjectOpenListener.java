/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.font;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import org.jetbrains.annotations.NotNull;

public class ProjectOpenListener implements ProjectManagerListener {
  //See https://plugins.jetbrains.com/docs/intellij/plugin-components.html#comintellijpoststartupactivity
  // for notice and documentation on the deprecation intentions of
  // Components from JetBrains.
  //
  // Migration forward has different directions before and after
  // 2023.1, if we can, it would be prudent to wait until we are
  // only supporting this major platform as a minimum version.
  //
  // https://github.com/flutter/flutter-intellij/issues/6953
  @Override
  public void projectOpened(@NotNull Project project) {
    // Ensure this isn't part of testing
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }
    FontPreviewProcessor.analyze(project);
  }
}
