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
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.flutter.FlutterUtils;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.devtools.DevToolsIdeFeature;
import io.flutter.devtools.DevToolsUrl;
import io.flutter.run.daemon.DevToolsService;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkVersion;
import io.flutter.utils.AsyncUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Optional;

public class DeepLinksViewFactory implements ToolWindowFactory {
  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    final ContentManager contentManager = toolWindow.getContentManager();
    FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    FlutterSdkVersion sdkVersion = sdk == null ? null : sdk.getVersion();
    if (sdkVersion == null || !sdkVersion.canUseDeepLinksTool()) {
      JLabel label = new JLabel("<html>Deep links isn't supported<br> for this version of the Flutter SDK.<br>The minimum version required is 3.19.0.</html>");
      label.setBorder(JBUI.Borders.empty(5));
      label.setHorizontalAlignment(SwingConstants.CENTER);
      final JPanel panel = new JPanel(new BorderLayout());
      panel.add(label, BorderLayout.CENTER);
      final Content content = contentManager.getFactory().createContent(panel, null, false);

      toolWindow.getContentManager().addContent(content);
      return;
    }

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

          final String color = ColorUtil.toHex(UIUtil.getEditorPaneBackground());
          final DevToolsUrl devToolsUrl = new DevToolsUrl(
            instance.host,
            instance.port,
            null,
            "deep-links",
            true,
            color,
            UIUtil.getFontSize(UIUtil.FontSize.NORMAL),
            sdkVersion,
            WorkspaceCache.getInstance(project),
            DevToolsIdeFeature.TOOL_WINDOW
          );

          ApplicationManager.getApplication().invokeLater(() -> {
            Optional.ofNullable(
              FlutterUtils.embeddedBrowser(project)).ifPresent(embeddedBrowser -> embeddedBrowser.openPanel(toolWindow, "Deep Links", devToolsUrl, (String err) -> {
              System.out.println(err);
            }));
          });
        }
      );
  }
}
