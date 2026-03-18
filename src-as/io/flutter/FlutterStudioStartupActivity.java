/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.flutter.dart.FlutterDartAnalysisServer;
import io.flutter.logging.PluginLogger;
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
    LOG.info("Project " + project.getName() + ": Executing non-blocking Studio startup");

    if (AndroidUtils.isAndroidProject(project)) {
      GradleUtils.addGradleListeners(project);
    }
    if (!AddToAppUtils.initializeAndDetectFlutter(project)) {
      return null;
    }
    // Unset this flag for all projects, mainly to ease the upgrade from 3.0.1 to 3.1.
    // TODO(messick) Delete once 3.0.x has 0 7DA's.
    //FlutterProjectCreator.disableUserConfig(project);
    return null;
  }

  @Override
  public void executeProjectStartup(@NotNull Project project) {
    ReadAction.nonBlocking(() -> doNonBlockingStartup(project)).expireWith(FlutterDartAnalysisServer.getInstance(project))
      .submit(AppExecutorUtil.getAppExecutorService());

    if (!FlutterModuleUtils.declaresFlutter(project)) {
      LOG.info("Project " + project.getName() + ": does not declare Flutter; exiting Studio startup before checking project view");
      return;
    }

    // Ensure Flutter project configuration is applied.
    // This logic was previously in FlutterStudioProjectOpenProcessor. However, that processor
    // caused hangs and crashes due to threading issues when delegating to the Platform processor (AS 2025.2).
    // Instead, we let the Platform open the project normally, and then apply our configuration here.
    // This includes ensuring the module type is set to 'flutter' (for icons/facets) and enabling the Dart SDK.
    // See https://github.com/flutter/flutter-intellij/issues/8661
    LOG.info("Checking for unconfigured Flutter modules in " + project.getName());
    for (Module module : FlutterModuleUtils.getModules(project)) {
      if (FlutterModuleUtils.declaresFlutter(module) && !FlutterModuleUtils.isFlutterModule(module)) {
        LOG.info("Fixing Flutter module configuration for " + module.getName());
        // TODO Add analytics when available
        ApplicationManager.getApplication().invokeLater(() -> {
          ApplicationManager.getApplication().runWriteAction(() -> {
            FlutterModuleUtils.setFlutterModuleType(module);
          });
          FlutterModuleUtils.enableDartSDK(module);
        });
      }
    }

    // Set project tool window to show the project on first open (Android Studio sometimes opens android view instead).
    // See https://github.com/flutter/flutter-intellij/issues/8556.
    // Note: After showing the project view, subsequent opens should go to the project view also. However, if the android subdirectory is
    // opened, it seems the full project settings will be modified to open to the Android view instead. See
    // https://github.com/flutter/flutter-intellij/pull/8573 for more details.
    PropertiesComponent properties = PropertiesComponent.getInstance(project);
    if (properties == null) return;
    if (!properties.isValueSet(IS_FIRST_OPEN_KEY)) {
      OpenApiUtils.safeInvokeLater(() -> {
        ProjectView projectView = ProjectView.getInstance(project);
        if (projectView == null) {
          return;
        }
        if (projectView.getPaneIds().contains(PROJECT_VIEW_ID)) {
          LOG.info("Project " + project.getName() + ": Setting project tool window to be project view on first open");
          projectView.changeView(PROJECT_VIEW_ID);
        }
      });
      properties.setValue(IS_FIRST_OPEN_KEY, "true");
    }
  }
}
