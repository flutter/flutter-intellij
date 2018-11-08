/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import io.flutter.FlutterMessages;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRoots;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterMakeHostAppEditableAction extends FlutterSdkAction {

  @Override
  public void startCommand(@NotNull Project project, @NotNull FlutterSdk sdk, @Nullable PubRoot root, @NotNull DataContext context) {
    if (root == null) {
      FlutterMessages.showError(
        "Cannot Find Pub Root",
        "Flutter make-host-app-editable can only be run within a directory with a pubspec.yaml file");
      return;
    }
    sdk.startMakeHostAppEditable(root, project);
    // TODO(messick): Automatically run AndroidFrameworkDetector here when invoked from Android Studio.
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null || project.isDefault()) {
      e.getPresentation().setEnabled(false);
      return;
    }
    boolean enabled = false;
    for (PubRoot root : PubRoots.forProject(project)) {
      if (root.isNonEditableFlutterModule()) {
        enabled = true;
        break;
      }
    }
    e.getPresentation().setEnabled(enabled);
  }
}
