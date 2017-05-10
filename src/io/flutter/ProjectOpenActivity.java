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
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.ui.Messages;
import icons.FlutterIcons;
import io.flutter.pub.PubRoot;
import io.flutter.sdk.FlutterSdk;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Runs startup actions just after a project is opened, before it's indexed.
 *
 * @see FlutterInitializer for actions that run later.
 */
public class ProjectOpenActivity implements StartupActivity, DumbAware {
  @Override
  public void runActivity(@NotNull Project project) {
    if (!FlutterModuleUtils.hasFlutterModule(project)) {
      // This method is called in twice when importing a project, because the project is reloaded.
      // Don't do anything until the second time.
      return;
    }

    PubRoot root = PubRoot.forProjectWithRefresh(project);
    if (root == null) {
      return;
    }

    if (!downloadDependencies(project, root)) {
      return;
    }

    root = root.refresh();
    if (root != null && !root.hasUpToDatePackages()) {
      Notifications.Bus.notify(new PackagesOutOfDateNotification(project));
    }
  }

  /**
   * Ensures that we can start the analysis server and it won't see bad imports.
   * <p>
   * We might need to download the Dart SDK or some packages.
   * <p>
   * Waits until download is complete. (On a slow network, this can take many seconds.)
   * <p>
   * Returns true if successful.
   */
  private boolean downloadDependencies(@NotNull Project project, @NotNull PubRoot root) {
    final FlutterSdk sdk = FlutterSdk.getIncomplete(project);
    if (sdk == null) {
      // Can't do anything without a Flutter SDK.
      return false;
    }

    if (root.getPackages() == null) {
      // Get packages; as a side effect this will also download the Dart SDK if needed.
      try {
        final Process process = sdk.startPackagesGet(root, project);
        if (process != null) {
          process.waitFor();
          return process.exitValue() == 0;
        }
      }
      catch (InterruptedException e) {
        FlutterMessages.showError("Error opening", e.getMessage());
      }
      return false;
    } else if (sdk.getDartSdkPath() == null) {
      // This can happen when opening an example project after "git clone flutter".
      // We have a .packages file, but need to run a flutter command to download the Dart SDK.
      final boolean ok = sdk.sync(project);
      if (!ok) {
        FlutterMessages.showError("Error opening project", "Failed to download Dart SDK for Flutter");
      }
      return ok;
    } else {
      return true; // Nothing to do.
    }
  }

  private static class PackagesOutOfDateNotification extends Notification {

    @NotNull
    private final Project myProject;

    public PackagesOutOfDateNotification(@NotNull Project project) {
      super("Flutter Packages", FlutterIcons.Flutter, "Flutter packages get.",
            null, "The pubspec.yaml file has been modified since " +
                  "the last time 'flutter packages get' was run.",
            NotificationType.INFORMATION, null);

      myProject = project;

      addAction(new AnAction("Run 'flutter packages get'") {
        @Override
        public void actionPerformed(AnActionEvent event) {
          // TODO(skybrian) analytics for the action? (The command is logged.)
          expire();

          final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
          if (sdk == null) {
            Messages.showErrorDialog(project, "Flutter SDK not found", "Error");
            return;
          }

          final PubRoot root = PubRoot.forProjectWithRefresh(project);
          if (root == null) {
            Messages.showErrorDialog("Pub root not found", "Error");
            return;
          }

          if (sdk.startPackagesGet(root, project) == null) {
            Messages.showErrorDialog("Unable to run 'flutter packages get'", "Error");
          }
        }
      });
    }
  }

  private static final Logger LOG = Logger.getInstance(ProjectOpenActivity.class.getName());
}
