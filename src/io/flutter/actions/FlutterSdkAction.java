/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import io.flutter.FlutterBundle;
import io.flutter.FlutterMessages;
import io.flutter.FlutterUtils;
import io.flutter.analytics.Analytics;
import io.flutter.analytics.AnalyticsConstants;
import io.flutter.analytics.AnalyticsData;
import io.flutter.bazel.Workspace;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRoots;
import io.flutter.sdk.FlutterSdk;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Base class for Flutter commands.
 */
public abstract class FlutterSdkAction extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    final Project project = DumbAwareAction.getEventProject(event);

    AnalyticsData analyticsData = AnalyticsData.forAction(this, event);

    if (enableActionInBazelContext()) {
      // See if the Bazel workspace exists for this project.
      final Workspace workspace = FlutterModuleUtils.getFlutterBazelWorkspace(project);
      if (workspace != null) {
        FileDocumentManager.getInstance().saveAllDocuments();
        startCommandInBazelContext(project, workspace, event);
        analyticsData.add(AnalyticsConstants.IN_BAZEL_CONTEXT, true);
        Analytics.report(analyticsData);
        return;
      }
    }

    final FlutterSdk sdk = project != null ? FlutterSdk.getFlutterSdk(project) : null;
    if (sdk == null) {
      showMissingSdkDialog(project);
      analyticsData.add(AnalyticsConstants.MISSING_SDK, true);
      Analytics.report(analyticsData);
      return;
    }

    FileDocumentManager.getInstance().saveAllDocuments();
    PubRoot root = PubRoot.forEventWithRefresh(event);
    @NotNull DataContext context = event.getDataContext();
    if (root != null) {
      startCommand(project, sdk, root, context);
    }
    else {
      List<PubRoot> roots = PubRoots.forProject(project);
      for (PubRoot sub : roots) {
        startCommand(project, sdk, sub, context);
      }
    }

    Analytics.report(analyticsData);
  }

  public abstract void startCommand(@NotNull Project project,
                                    @NotNull FlutterSdk sdk,
                                    @Nullable PubRoot root,
                                    @NotNull DataContext context);

  /**
   * Implemented by actions which are used in the Bazel context ({@link #enableActionInBazelContext()} returns true), by default this method
   * throws an {@link Error}.
   */
  public void startCommandInBazelContext(@NotNull Project project, @NotNull Workspace workspace, @NotNull AnActionEvent event) {
    throw new Error("This method should not be called directly, but should be overridden.");
  }

  /**
   * By default, this method returns false. For actions which can be used in the Bazel context this method should return true.
   */
  public boolean enableActionInBazelContext() {
    return false;
  }

  public static void showMissingSdkDialog(@Nullable Project project) {
    final int response = FlutterMessages.showDialog(project, FlutterBundle.message("flutter.sdk.notAvailable.message"),
                                                    FlutterBundle.message("flutter.sdk.notAvailable.title"),
                                                    new String[]{"Yes, configure", "No, thanks"}, -1);
    if (response == 0) {
      FlutterUtils.openFlutterSettings(project);
    }
  }
}