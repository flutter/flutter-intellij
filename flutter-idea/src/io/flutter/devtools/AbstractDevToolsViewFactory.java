/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.devtools;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import io.flutter.FlutterUtils;
import io.flutter.actions.RefreshToolWindowAction;
import io.flutter.run.daemon.DevToolsInstance;
import io.flutter.run.daemon.DevToolsService;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkVersion;
import io.flutter.utils.AsyncUtils;
import io.flutter.utils.OpenApiUtils;
import io.flutter.view.EmbeddedBrowser;
import io.flutter.view.ViewUtils;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public abstract class AbstractDevToolsViewFactory implements ToolWindowFactory {

  @NotNull
  protected final ViewUtils viewUtils = new ViewUtils();

  public abstract boolean versionSupportsThisTool(@NotNull FlutterSdkVersion flutterSdkVersion);

  @NotNull
  public abstract String getToolWindowId();

  @NotNull
  public abstract String getToolWindowTitle();

  @NotNull
  public abstract DevToolsUrl getDevToolsUrl(@NotNull Project project,
                                             @NotNull FlutterSdkVersion flutterSdkVersion,
                                             @NotNull DevToolsInstance instance);

  protected void doAfterBrowserOpened(@NotNull Project project, @NotNull EmbeddedBrowser browser) {}

  @Override
  public Object isApplicableAsync(@NotNull Project project, @NotNull Continuation<? super Boolean> $completion) {
    // Due to https://github.com/flutter/flutter/issues/142521, this always returns true when the
    // Flutter IJ plugin is installed.

    // The logic which asserts that the Flutter SDK is up to date enough for this particular feature
    // is captured in the implementation of createToolWindowContent() below.

    return true;
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    final FlutterSdk flutterSdk = FlutterSdk.getFlutterSdk(project);
    final FlutterSdkVersion flutterSdkVersion = flutterSdk == null ? null : flutterSdk.getVersion();

    // There are four potential states for the Flutter SDK:
    // 1. The Flutter SDK is null (flutterSdk == null) (an SDK path is invalid or not configured)
    // 2. The Flutter SDK exists, but version file information is unavailable (flutterSdkVersion == null),
    //      see https://github.com/flutter/flutter/issues/142521.
    // 3. The Flutter SDK exists, and the version file information is available, but this tool is not
    //      available on the version of the SDK that the user has.
    // 4. The Flutter SDK exists with valid version information.

    // First case:
    if (flutterSdk == null) {
      viewUtils.presentLabels(toolWindow, List.of("Set the Flutter SDK path in",
                                                  "Settings > Languages & Frameworks > Flutter,",
                                                  "and then restart the IDE."));
      return;
    }

    // Second case:
    if (flutterSdk.getVersion().fullVersion().equals(FlutterSdkVersion.UNKNOWN_VERSION)) {
      viewUtils.presentLabels(toolWindow, List.of("A Flutter SDK was found at the location",
                                                  "specified in the settings, however the directory",
                                                  "is in an incomplete state. To fix, shut down the IDE,",
                                                  "run `flutter doctor` or `flutter --version`",
                                                  "and then restart the IDE."));
      return;
    }

    // Third case:
    if (!versionSupportsThisTool(flutterSdkVersion)) {
      final String versionText = flutterSdkVersion.fullVersion();
      viewUtils.presentLabels(toolWindow, List.of("The version of your Flutter SDK,",
                                                  versionText + ",",
                                                  "is not recent enough to use this tool.",
                                                  "Update the Flutter SDK, `flutter upgrade`,",
                                                  "and then restart the IDE."));
      return;
    }

    // Final case:
    AsyncUtils.whenCompleteUiThread(
      DevToolsService.getInstance(project).getDevToolsInstance(),
      (instance, error) -> {
        // Skip displaying if the project has been closed.
        if (!project.isOpen()) {
          return;
        }

        if (error != null) {
          return;
        }

        if (instance == null) {
          return;
        }

        final DevToolsUrl devToolsUrl = getDevToolsUrl(project, flutterSdkVersion, instance);

        OpenApiUtils.safeInvokeLater(() -> {
          Optional.ofNullable(
              FlutterUtils.embeddedBrowser(project))
            .ifPresent(embeddedBrowser ->
                       {
                         embeddedBrowser.openPanel(toolWindow, getToolWindowTitle(), devToolsUrl, System.out::println);
                         doAfterBrowserOpened(project, embeddedBrowser);
                       });
        });
      }
    );

    // TODO(helin24): It may be better to add this to the gear actions or to attach as a mouse event on individual tabs within a tool
    //  window, but I wasn't able to get either working immediately.
    toolWindow.setTitleActions(List.of(new RefreshToolWindowAction(getToolWindowId())));
  }
}