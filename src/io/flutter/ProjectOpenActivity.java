/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.intellij.framework.FrameworkType;
import com.intellij.framework.detection.DetectionExcludesConfiguration;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectType;
import com.intellij.openapi.project.ProjectTypeService;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.ui.Messages;
import icons.FlutterIcons;
import io.flutter.analytics.TimeTracker;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.jxbrowser.JxBrowserManager;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRoots;
import io.flutter.sdk.FlutterSdk;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.AndroidUtils;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.android.facet.AndroidFrameworkDetector;
import org.jetbrains.annotations.NotNull;

/**
 * Runs startup actions just after a project is opened, before it's indexed.
 *
 * @see FlutterInitializer for actions that run later.
 */
public class ProjectOpenActivity implements StartupActivity, DumbAware {
  public static final ProjectType FLUTTER_PROJECT_TYPE = new ProjectType("io.flutter");
  private static final Logger LOG = Logger.getInstance(ProjectOpenActivity.class);

  public ProjectOpenActivity() {
  }

  @Override
  public void runActivity(@NotNull Project project) {
    TimeTracker.getInstance(project).onProjectOpen();

    // TODO(messick): Remove 'FlutterUtils.isAndroidStudio()' after Android Q sources are published.
    if (FlutterUtils.isAndroidStudio() && AndroidUtils.isAndroidProject(project)) {
      AndroidUtils.addGradleListeners(project);
    }

    // TODO(helinx): We don't have a good way to check whether a Bazel project is using Flutter. Look into whether we can
    // build a better Flutter Bazel check into `declaresFlutter` so we don't need the second condition.
    if (!FlutterModuleUtils.declaresFlutter(project) && !WorkspaceCache.getInstance(project).isBazel()) {
      return;
    }

    // Set up JxBrowser listening and check if it's already enabled.
    JxBrowserManager.getInstance().listenForSettingChanges(project);
    if (FlutterSettings.getInstance().isEnableEmbeddedBrowsers()) {
      JxBrowserManager.getInstance().setUp(project);
    }

    final FlutterSdk sdk = FlutterSdk.getIncomplete(project);
    if (sdk == null) {
      // We can't do anything without a Flutter SDK.
      return;
    }
    // TODO(messick) Re-enable this after dropping support for 2020.2.
    //excludeAndroidFrameworkDetector(project);

    // Report time when indexing finishes.
    DumbService.getInstance(project).runWhenSmart(() -> {
      FlutterInitializer.getAnalytics().sendEventMetric(
        "startup",
        "indexingFinished",
        project.getService(TimeTracker.class).millisSinceProjectOpen()
      );
    });

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      sdk.queryFlutterConfig("android-studio-dir", false);
    });
    if (FlutterUtils.isAndroidStudio() && !FLUTTER_PROJECT_TYPE.equals(ProjectTypeService.getProjectType(project))) {
      if (!AndroidUtils.isAndroidProject(project)) {
        ProjectTypeService.setProjectType(project, FLUTTER_PROJECT_TYPE);
      }
    }

    // If this project is intended as a bazel project, don't run the pub alerts.
    if (WorkspaceCache.getInstance(project).isBazel()) {
      return;
    }

    for (PubRoot pubRoot : PubRoots.forProject(project)) {
      if (!pubRoot.hasUpToDatePackages()) {
        Notifications.Bus.notify(new PackagesOutOfDateNotification(project, pubRoot), project);
      }
    }
  }

  private static void excludeAndroidFrameworkDetector(@NotNull Project project) {
    DetectionExcludesConfiguration excludesConfiguration = DetectionExcludesConfiguration.getInstance(project);
    FrameworkType type = new AndroidFrameworkDetector().getFrameworkType();
    if (!excludesConfiguration.isExcludedFromDetection(type)) {
      excludesConfiguration.addExcludedFramework(type);
    }
  }

  private static class PackagesOutOfDateNotification extends Notification {
    @NotNull private final Project myProject;
    @NotNull private final PubRoot myRoot;

    public PackagesOutOfDateNotification(@NotNull Project project, @NotNull PubRoot root) {
      super("Flutter Packages", FlutterIcons.Flutter, "Flutter pub get.",
            null, "The pubspec.yaml file has been modified since " +
                  "the last time 'flutter pub get' was run.",
            NotificationType.INFORMATION, null);

      myProject = project;
      myRoot = root;

      addAction(new AnAction("Run 'flutter pub get'") {
        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
          expire();

          final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
          if (sdk == null) {
            Messages.showErrorDialog(project, "Flutter SDK not found", "Error");
            return;
          }

          if (sdk.startPubGet(root, project) == null) {
            Messages.showErrorDialog("Unable to run 'flutter pub get'", "Error");
          }
        }
      });
    }
  }
}
