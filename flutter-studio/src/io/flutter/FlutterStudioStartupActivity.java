/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import io.flutter.android.AndroidModuleLibraryManager;
import io.flutter.project.FlutterProjectCreator;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.AddToAppUtils;
import io.flutter.utils.AndroidUtils;
import io.flutter.utils.GradleUtils;
import org.jetbrains.annotations.NotNull;

public class FlutterStudioStartupActivity implements StartupActivity {

  @Override
  public void runActivity(@NotNull Project project) {
    if (AndroidUtils.isAndroidProject(project)) {
      GradleUtils.addGradleListeners(project);
    }
    if (!AddToAppUtils.initializeAndDetectFlutter(project)) {
      return;
    }

    // Unset this flag for all projects, mainly to ease the upgrade from 3.0.1 to 3.1.
    // TODO(messick) Delete once 3.0.x has 0 7DA's.
    FlutterProjectCreator.disableUserConfig(project);
    // Monitor Android dependencies.
    if (FlutterSettings.getInstance().isSyncingAndroidLibraries() ||
        System.getProperty("flutter.android.library.sync", null) != null) {
      // TODO(messick): Remove the flag once this sync mechanism is stable.
      AndroidModuleLibraryManager.startWatching(project);
    }
  }
}
