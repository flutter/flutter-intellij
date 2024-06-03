/*
 * Copyright 2024 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.deeplinks;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentManager;
import io.flutter.FlutterUtils;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.devtools.DevToolsIdeFeature;
import io.flutter.devtools.DevToolsUrl;
import io.flutter.run.daemon.DevToolsService;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkVersion;
import io.flutter.utils.AsyncUtils;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class DeepLinksViewFactory implements ToolWindowFactory {
  @Override
  public Object isApplicableAsync(@NotNull Project project, @NotNull Continuation<? super Boolean> $completion) {
    FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    FlutterSdkVersion sdkVersion = sdk == null ? null : sdk.getVersion();
    return sdkVersion != null && sdkVersion.canUseDeepLinksTool();
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    final ContentManager contentManager = toolWindow.getContentManager();
    FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    FlutterSdkVersion sdkVersion = sdk == null ? null : sdk.getVersion();

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

        final DevToolsUrl devToolsUrl = new DevToolsUrl.Builder()
          .setDevToolsHost(instance.host)
          .setDevToolsPort(instance.port)
          .setPage("deep-links")
          .setEmbed(true)
          .setFlutterSdkVersion(sdkVersion)
          .setWorkspaceCache(WorkspaceCache.getInstance(project))
          .setIdeFeature(DevToolsIdeFeature.TOOL_WINDOW)
          .build();

        ApplicationManager.getApplication().invokeLater(() -> {
          Optional.ofNullable(
              FlutterUtils.embeddedBrowser(project))
            .ifPresent(embeddedBrowser -> embeddedBrowser.openPanel(toolWindow, "Deep Links", devToolsUrl, (String err) -> {
              System.out.println(err);
            }));
        });
      }
    );
  }
}
