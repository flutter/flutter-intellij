/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectType;
import com.intellij.openapi.project.ProjectTypeService;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.ui.Messages;
import icons.FlutterIcons;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRoots;
import io.flutter.sdk.FlutterSdk;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Runs startup actions just after a project is opened, before it's indexed.
 *
 * @see FlutterInitializer for actions that run later.
 */
public class ProjectOpenActivity implements StartupActivity, DumbAware {
  public static final ProjectType FLUTTER_PROJECT_TYPE = new ProjectType("io.flutter");
  private static final Logger LOG = Logger.getInstance(ProjectOpenActivity.class);

  @Override
  public void runActivity(@NotNull Project project) {
    if (!FlutterModuleUtils.declaresFlutter(project)) {
      return;
    }

    final FlutterSdk sdk = FlutterSdk.getIncomplete(project);
    if (sdk == null) {
      // We can't do anything without a Flutter SDK.
      return;
    }

    for (PubRoot pubRoot : PubRoots.forProject(project)) {
      if (!pubRoot.hasUpToDatePackages()) {
        Notifications.Bus.notify(new PackagesOutOfDateNotification(project, pubRoot));
      }
    }
    if (!FLUTTER_PROJECT_TYPE.equals(ProjectTypeService.getProjectType(project))) {
      ProjectTypeService.setProjectType(project, FLUTTER_PROJECT_TYPE);
    }
  }

  private static class PackagesOutOfDateNotification extends Notification {
    @NotNull private final Project myProject;
    @NotNull private final PubRoot myRoot;

    public PackagesOutOfDateNotification(@NotNull Project project, @NotNull PubRoot root) {
      super("Flutter Packages", FlutterIcons.Flutter, "Flutter packages get.",
            null, "The pubspec.yaml file has been modified since " +
                  "the last time 'flutter packages get' was run.",
            NotificationType.INFORMATION, null);

      myProject = project;
      myRoot = root;

      addAction(new AnAction("Run 'flutter packages get'") {
        @Override
        public void actionPerformed(@NotNull AnActionEvent event) {
          expire();

          final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
          if (sdk == null) {
            Messages.showErrorDialog(project, "Flutter SDK not found", "Error");
            return;
          }

          if (sdk.startPackagesGet(root, project) == null) {
            Messages.showErrorDialog("Unable to run 'flutter packages get'", "Error");
          }
        }
      });
    }
  }
}
