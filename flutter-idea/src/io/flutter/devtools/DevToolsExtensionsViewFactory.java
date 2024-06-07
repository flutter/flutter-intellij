/*
 * Copyright 2024 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.devtools;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentManager;
import io.flutter.FlutterUtils;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.run.daemon.DevToolsService;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkVersion;
import io.flutter.utils.AsyncUtils;
import io.flutter.view.FlutterViewMessages;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class DevToolsExtensionsViewFactory implements ToolWindowFactory {
  public static void init(Project project) {
    project.getMessageBus().connect().subscribe(
      FlutterViewMessages.FLUTTER_DEBUG_TOPIC, (FlutterViewMessages.FlutterDebugNotifier)event -> initView(project, event)
    );
  }

  private static void initView(Project project, FlutterViewMessages.FlutterDebugEvent event) {
    DevToolsExtensionsViewService service = project.getService(DevToolsExtensionsViewService.class);
    String vmServiceUri = event.app.getConnector().getBrowserUrl();
    if (vmServiceUri == null) return;
    service.updateVmServiceUri(vmServiceUri);
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow window) {
    final ContentManager contentManager = window.getContentManager();
    FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    FlutterSdkVersion sdkVersion = sdk == null ? null : sdk.getVersion();
    RemainingDevToolsViewService service = project.getService(RemainingDevToolsViewService.class);

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
          .setHide("all-except-extensions")
          .setEmbed(true).setFlutterSdkVersion(sdkVersion)
          .setWorkspaceCache(WorkspaceCache.getInstance(project))
          .setIdeFeature(DevToolsIdeFeature.TOOL_WINDOW)
          .build();

        ApplicationManager.getApplication().invokeLater(() -> {
          Optional.ofNullable(
              FlutterUtils.embeddedBrowser(project))
            .ifPresent(embeddedBrowser -> {
              service.setEmbeddedBrowser(embeddedBrowser);
              embeddedBrowser.openPanel(window, "Flutter DevTools", devToolsUrl, (String err) -> {
                System.out.println(err);
              });
            });
        });
      }
    );
  }

  @Nullable
  @Override
  public Object isApplicableAsync(@NotNull Project project, @NotNull Continuation<? super Boolean> $completion) {
    FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    FlutterSdkVersion sdkVersion = sdk == null ? null : sdk.getVersion();
    return sdkVersion != null && sdkVersion.canUseDevToolsMultiEmbed();
  }
}
