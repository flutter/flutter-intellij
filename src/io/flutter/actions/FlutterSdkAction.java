/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import io.flutter.FlutterBundle;
import io.flutter.FlutterInitializer;
import io.flutter.FlutterMessages;
import io.flutter.FlutterUtils;
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

    final FlutterSdk sdk = project != null ? FlutterSdk.getFlutterSdk(project) : null;
    if (sdk == null) {
      showMissingSdkDialog(project);
      return;
    }

    try {
      FlutterInitializer.sendAnalyticsAction(this);
      FileDocumentManager.getInstance().saveAllDocuments();
      startCommand(project, sdk, PubRoot.forEventWithRefresh(event));
    }
    catch (ExecutionException e) {
      FlutterMessages.showError(
        FlutterBundle.message("flutter.command.exception.title"),
        FlutterBundle.message("flutter.command.exception.message", e.getMessage()));
      LOG.warn(e);
    }
  }

  public abstract void startCommand(@NotNull Project project, @NotNull FlutterSdk sdk, @Nullable PubRoot root) throws ExecutionException;

  private static void showMissingSdkDialog(Project project) {
    final int response = FlutterMessages.showDialog(project, FlutterBundle.message("flutter.sdk.notAvailable.message"),
                                                    FlutterBundle.message("flutter.sdk.notAvailable.title"),
                                                    new String[]{"Yes, configure", "No, thanks"}, -1);
    if (response == 0) {
      FlutterUtils.openFlutterSettings(project);
    }
  }
}
