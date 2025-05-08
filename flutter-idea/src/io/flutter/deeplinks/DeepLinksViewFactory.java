/*
 * Copyright 2024 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.deeplinks;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import io.flutter.FlutterUtils;
import io.flutter.actions.RefreshToolWindowAction;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.devtools.DevToolsIdeFeature;
import io.flutter.devtools.DevToolsUrl;
import io.flutter.run.daemon.DevToolsService;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkVersion;
import io.flutter.utils.AsyncUtils;
import io.flutter.utils.OpenApiUtils;
import io.flutter.view.ViewUtils;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public class DeepLinksViewFactory implements ToolWindowFactory {
  @NotNull private static String TOOL_WINDOW_ID = "Flutter Deep Links";

  @NotNull
  private final ViewUtils viewUtils = new ViewUtils();

  @Override
  public Object isApplicableAsync(@NotNull Project project, @NotNull Continuation<? super Boolean> $completion) {
    FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    FlutterSdkVersion sdkVersion = sdk == null ? null : sdk.getVersion();
    return sdkVersion != null && sdkVersion.canUseDeepLinksTool();
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    FlutterSdkVersion sdkVersion = sdk == null ? null : sdk.getVersion();

    AsyncUtils.whenCompleteUiThread(
      DevToolsService.getInstance(project).getDevToolsInstance(),
      (instance, error) -> {
        final boolean inValidState = viewUtils.verifyDevToolsPanelStateIsValid(toolWindow, project, instance, error);
        if (!inValidState) {
          return;
        }

        final DevToolsUrl devToolsUrl = new DevToolsUrl.Builder()
          .setDevToolsHost(instance.host())
          .setDevToolsPort(instance.port())
          .setPage("deep-links")
          .setEmbed(true)
          .setFlutterSdkVersion(sdkVersion)
          .setWorkspaceCache(WorkspaceCache.getInstance(project))
          .setIdeFeature(DevToolsIdeFeature.TOOL_WINDOW)
          .build();

        OpenApiUtils.safeInvokeLater(() -> {
          Optional.ofNullable(
              FlutterUtils.embeddedBrowser(project))
            .ifPresent(embeddedBrowser -> embeddedBrowser.openPanel(toolWindow, "Deep Links", devToolsUrl, System.out::println));
        });
      }
    );

    // TODO(helin24): It may be better to add this to the gear actions or to attach as a mouse event on individual tabs within a tool
    //  window, but I wasn't able to get either working immediately.
    toolWindow.setTitleActions(List.of(new RefreshToolWindowAction(TOOL_WINDOW_ID)));
  }
}
