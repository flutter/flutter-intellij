/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.flutter.android.AndroidModuleLibraryManager;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.logging.PluginLogger;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.AddToAppUtils;
import io.flutter.utils.AndroidUtils;
import io.flutter.utils.FlutterModuleUtils;
import io.flutter.utils.GradleUtils;
import io.flutter.utils.OpenApiUtils;
import org.jetbrains.annotations.NotNull;

public class FlutterStudioStartupActivity extends FlutterProjectActivity {
  private static @NotNull Logger LOG = PluginLogger.createLogger(FlutterStudioStartupActivity.class);
  private static final String IS_FIRST_OPEN_KEY = "io.flutter.project.isFirstOpen";
  private static final String PROJECT_VIEW_ID = "ProjectPane";

  private static Void doNonBlockingStartup(@NotNull Project project) {
    LOG.info("Project " + project.getName() + ": Executing Studio startup");

    if (AndroidUtils.isAndroidProject(project)) {
      GradleUtils.addGradleListeners(project);
    }
    if (!AddToAppUtils.initializeAndDetectFlutter(project)) {
      return null;
    }
    // Unset this flag for all projects, mainly to ease the upgrade from 3.0.1 to 3.1.
    // TODO(messick) Delete once 3.0.x has 0 7DA's.
    //FlutterProjectCreator.disableUserConfig(project);
    // Monitor Android dependencies.
    FlutterSettings flutterSettings = FlutterSettings.getInstance();
    if (flutterSettings.isSyncingAndroidLibraries() || System.getProperty("flutter.android.library.sync", null) != null) {
      // TODO(messick): Remove the flag once this sync mechanism is stable.
      AndroidModuleLibraryManager.startWatching(project);
    }
    return null;
  }

  @Override
  public void executeProjectStartup(@NotNull Project project) {
    ReadAction.nonBlocking(() -> doNonBlockingStartup(project)).expireWith(FlutterDartAnalysisServer.getInstance(project))
      .submit(AppExecutorUtil.getAppExecutorService());

    if (!FlutterModuleUtils.declaresFlutter(project)) {
      LOG.info("Project " + project.getName() + ": does not declare Flutter; exiting Studio startup before setting project view");
      return;
    }

    // Set project tool window to show the project on first open (Android Studio sometimes opens android view instead).
    PropertiesComponent properties = PropertiesComponent.getInstance(project);
    if (properties == null) return;
    if (!properties.isValueSet(IS_FIRST_OPEN_KEY)) {
      OpenApiUtils.safeInvokeLater(() -> {
        ProjectView projectView = ProjectView.getInstance(project);
        if (projectView == null) return;
        if (projectView.getPaneIds().contains(PROJECT_VIEW_ID)) {
          LOG.info("Project " + project.getName() + ": Setting project tool window to be project view");
          projectView.changeView(PROJECT_VIEW_ID);
        }
      });
      properties.setValue(IS_FIRST_OPEN_KEY, "true");
    }
  }
}
