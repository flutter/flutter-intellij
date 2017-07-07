/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import io.flutter.FlutterBundle;
import io.flutter.FlutterInitializer;
import io.flutter.FlutterMessages;
import io.flutter.FlutterUtils;
import io.flutter.bazel.Workspace;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.pub.PubRoot;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for Flutter commands.
 */
public abstract class FlutterSdkAction extends DumbAwareAction {

  private static final Logger LOG = Logger.getInstance(FlutterSdkAction.class);

  @Override
  public void actionPerformed(AnActionEvent event) {
    final Project project = DumbAwareAction.getEventProject(event);

    if(enableActionInBazelContext() && project != null) {
      // See if the Bazel workspace provides a script.
      final Workspace workspace = WorkspaceCache.getInstance(project).getNow();
      if (workspace != null) {
          FlutterInitializer.sendAnalyticsAction(this);
          FileDocumentManager.getInstance().saveAllDocuments();
          startCommandInBazelContext(project, workspace);
      }
    } else {
      final FlutterSdk sdk = project != null ? FlutterSdk.getFlutterSdk(project) : null;
      if (sdk == null) {
        showMissingSdkDialog(project);
        return;
      }

      FlutterInitializer.sendAnalyticsAction(this);
      FileDocumentManager.getInstance().saveAllDocuments();
      startCommand(project, sdk, PubRoot.forEventWithRefresh(event));
    }
  }

  public abstract void startCommand(@NotNull Project project, @NotNull FlutterSdk sdk, @Nullable PubRoot root);

  public void startCommandInBazelContext(@NotNull Project project, @NotNull Workspace workspace) {
  }

  public boolean enableActionInBazelContext() {
    return false;
  }

  private static void showMissingSdkDialog(Project project) {
    final int response = FlutterMessages.showDialog(project, FlutterBundle.message("flutter.sdk.notAvailable.message"),
                                                    FlutterBundle.message("flutter.sdk.notAvailable.title"),
                                                    new String[]{"Yes, configure", "No, thanks"}, -1);
    if (response == 0) {
      FlutterUtils.openFlutterSettings(project);
    }
  }
}
