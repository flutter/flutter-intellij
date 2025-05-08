/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.propertyeditor;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.dart.DartPlugin;
import io.flutter.dart.DartPluginVersion;
import io.flutter.devtools.AbstractDevToolsViewFactory;
import io.flutter.devtools.DevToolsIdeFeature;
import io.flutter.devtools.DevToolsUrl;
import io.flutter.run.daemon.DevToolsInstance;
import io.flutter.sdk.FlutterSdkVersion;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PropertyEditorViewFactory extends AbstractDevToolsViewFactory {
  @NotNull public static String TOOL_WINDOW_ID = "Flutter Property Editor";

  @NotNull public static String DEVTOOLS_PAGE_ID = "propertyEditor";

  @Override
  public boolean versionSupportsThisTool(@NotNull final FlutterSdkVersion flutterSdkVersion) {
    return flutterSdkVersion.canUsePropertyEditor();
  }

  @Override
  @NotNull
  public String getToolWindowId() {
    return TOOL_WINDOW_ID;
  }

  @Override
  @NotNull
  public String getToolWindowTitle() {
    return "Property Editor";
  }

  @Override
  @NotNull
  public DevToolsUrl getDevToolsUrl(@NotNull final Project project,
                                    @NotNull final FlutterSdkVersion flutterSdkVersion,
                                    @NotNull final DevToolsInstance instance) {
    return new DevToolsUrl.Builder()
      .setDevToolsHost(instance.host())
      .setDevToolsPort(instance.port())
      .setPage(DEVTOOLS_PAGE_ID)
      .setEmbed(true)
      .setFlutterSdkVersion(flutterSdkVersion)
      .setWorkspaceCache(WorkspaceCache.getInstance(project))
      .setIdeFeature(DevToolsIdeFeature.TOOL_WINDOW)
      .build();
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    final DartPluginVersion dartPluginVersion = DartPlugin.getDartPluginVersion();
    if (!dartPluginVersion.supportsPropertyEditor()) {
      super.viewUtils.presentLabels(toolWindow, List.of("The Flutter Property Editor requires a",
                                                        "newer version of the Dart plugin."));
      return;
    }
    super.createToolWindowContent(project, toolWindow);
  }
}
