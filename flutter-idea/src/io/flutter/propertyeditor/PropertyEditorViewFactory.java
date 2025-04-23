/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.propertyeditor;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.UIUtil;
import io.flutter.FlutterUtils;
import io.flutter.actions.RefreshToolWindowAction;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.dart.DartPlugin;
import io.flutter.dart.DartPluginVersion;
import io.flutter.devtools.DevToolsIdeFeature;
import io.flutter.devtools.DevToolsUrl;
import io.flutter.run.daemon.DevToolsService;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkVersion;
import io.flutter.utils.AsyncUtils;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Optional;

import static io.flutter.sdk.FlutterSdkVersion.MIN_SUPPORTS_PROPERTY_EDITOR;

public class PropertyEditorViewFactory implements ToolWindowFactory {
  @NotNull private static String TOOL_WINDOW_ID = "Flutter Property Editor";

  @Override
  public Object isApplicableAsync(@NotNull Project project, @NotNull Continuation<? super Boolean> $completion) {
    FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    FlutterSdkVersion sdkVersion = sdk == null ? null : sdk.getVersion();
    return sdkVersion != null && sdkVersion.canUsePropertyEditor();
  }

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    FlutterSdkVersion sdkVersion = sdk == null ? null : sdk.getVersion();

    DartPluginVersion dartPluginVersion = DartPlugin.getDartPluginVersion();
    if (!dartPluginVersion.supportsPropertyEditor()) {
      presentLabel(toolWindow, "Flutter Property Editor requires a newer version of the Dart plugin.");
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

        final DevToolsUrl devToolsUrl = new DevToolsUrl.Builder()
          .setDevToolsHost(instance.host())
          .setDevToolsPort(instance.port())
          .setPage("propertyEditor")
          .setEmbed(true)
          .setFlutterSdkVersion(sdkVersion)
          .setWorkspaceCache(WorkspaceCache.getInstance(project))
          .setIdeFeature(DevToolsIdeFeature.TOOL_WINDOW)
          .build();

        ApplicationManager.getApplication().invokeLater(() -> {
          Optional.ofNullable(
              FlutterUtils.embeddedBrowser(project))
            .ifPresent(embeddedBrowser -> embeddedBrowser.openPanel(toolWindow, "Property Editor", devToolsUrl, System.out::println));
        });
      }
    );

    toolWindow.setTitleActions(List.of(new RefreshToolWindowAction(TOOL_WINDOW_ID)));
  }

  protected void presentLabel(ToolWindow toolWindow, String text) {
    final JBLabel label = new JBLabel(text, SwingConstants.CENTER);
    label.setForeground(UIUtil.getLabelDisabledForeground());
    replacePanelLabel(toolWindow, label);
  }

  private void replacePanelLabel(ToolWindow toolWindow, JComponent label) {
    ApplicationManager.getApplication().invokeLater(() -> {
      final ContentManager contentManager = toolWindow.getContentManager();
      if (contentManager.isDisposed()) {
        return;
      }

      final JPanel panel = new JPanel(new BorderLayout());
      panel.add(label, BorderLayout.CENTER);
      final Content content = contentManager.getFactory().createContent(panel, null, false);
      contentManager.removeAllContents(true);
      contentManager.addContent(content);
    });
  }

}
