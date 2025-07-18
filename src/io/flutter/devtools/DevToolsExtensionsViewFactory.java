/*
 * Copyright 2024 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.devtools;

import com.intellij.openapi.project.Project;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.run.daemon.DevToolsInstance;
import io.flutter.sdk.FlutterSdkVersion;
import io.flutter.view.EmbeddedBrowser;
import io.flutter.view.FlutterViewMessages;
import org.jetbrains.annotations.NotNull;

public class DevToolsExtensionsViewFactory extends AbstractDevToolsViewFactory {
  @NotNull public static String TOOL_WINDOW_ID = "Flutter DevTools Extensions";

  public static void init(@NotNull final Project project) {
    project.getMessageBus().connect().subscribe(
      FlutterViewMessages.FLUTTER_DEBUG_TOPIC, (FlutterViewMessages.FlutterDebugNotifier)event -> initView(project, event)
    );
  }

  private static void initView(@NotNull final Project project, FlutterViewMessages.FlutterDebugEvent event) {
    DevToolsExtensionsViewService service = project.getService(DevToolsExtensionsViewService.class);
    String vmServiceUri = event.app.getConnector().getBrowserUrl();
    if (service == null || vmServiceUri == null) return;
    service.updateVmServiceUri(vmServiceUri);
  }

  @Override
  public boolean versionSupportsThisTool(@NotNull final FlutterSdkVersion flutterSdkVersion) {
    return flutterSdkVersion.canUseDevToolsMultiEmbed();
  }

  @Override
  @NotNull
  public String getToolWindowId() {
    return TOOL_WINDOW_ID;
  }

  @NotNull
  public String getToolWindowTitle() {
    return "Flutter DevTools";
  }

  @Override
  protected void doAfterBrowserOpened(@NotNull final Project project, @NotNull final EmbeddedBrowser embeddedBrowser) {
    DevToolsExtensionsViewService service = project.getService(DevToolsExtensionsViewService.class);
    if (service == null) return;
    service.setEmbeddedBrowser(embeddedBrowser);
  }

  @Override
  @NotNull
  public DevToolsUrl getDevToolsUrl(@NotNull final Project project,
                                    @NotNull final FlutterSdkVersion flutterSdkVersion,
                                    @NotNull final DevToolsInstance instance) {
    return new DevToolsUrl.Builder()
      .setDevToolsHost(instance.host())
      .setDevToolsPort(instance.port())
      .setHide("all-except-extensions")
      .setEmbed(true).setFlutterSdkVersion(flutterSdkVersion)
      .setWorkspaceCache(WorkspaceCache.getInstance(project))
      .setIdeFeature(DevToolsIdeFeature.TOOL_WINDOW)
      .build();
  }
}
