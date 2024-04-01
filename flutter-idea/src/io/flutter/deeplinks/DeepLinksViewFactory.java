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
import com.intellij.ui.ColorUtil;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.UIUtil;
import io.flutter.FlutterUtils;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.devtools.DevToolsIdeFeature;
import io.flutter.devtools.DevToolsUrl;
import io.flutter.run.daemon.DevToolsService;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkVersion;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.AsyncUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class DeepLinksViewFactory implements ToolWindowFactory {
  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
      AsyncUtils.whenCompleteUiThread(
        DevToolsService.getInstance(project).getDevToolsInstance(),
        (instance, error) -> {
          // Skip displaying if the project has been closed.
          if (!project.isOpen()) {
            return;
          }

          //if (error != null) {
          //  LOG.error(error);
          //  presentLabel(toolWindow, DEVTOOLS_FAILED_LABEL);
          //  return;
          //}

          if (instance == null) {
            //presentLabel(toolWindow, DEVTOOLS_FAILED_LABEL);
            return;
          }

          FlutterSdk flutterSdk = FlutterSdk.getFlutterSdk(project);
          FlutterSdkVersion flutterSdkVersion = flutterSdk == null ? null : flutterSdk.getVersion();

          final ContentManager contentManager = toolWindow.getContentManager();
          final String color = ColorUtil.toHex(UIUtil.getEditorPaneBackground());
          final DevToolsUrl devToolsUrl = new DevToolsUrl(
            instance.host,
            instance.port,
            null,
            "deep-links",
            true,
            color,
            UIUtil.getFontSize(UIUtil.FontSize.NORMAL),
            flutterSdkVersion,
            WorkspaceCache.getInstance(project),
            DevToolsIdeFeature.TOOL_WINDOW
          );

          ApplicationManager.getApplication().invokeLater(() -> {
            Optional.ofNullable(
              FlutterUtils.embeddedBrowser(project)).ifPresent(embeddedBrowser -> embeddedBrowser.openPanel(contentManager, "Deep Links", devToolsUrl, (String err) -> {
              System.out.println(err);
            }));
          });
        }
      );
  }
}
