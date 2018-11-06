/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.UIUtil;
import icons.FlutterIcons;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.run.daemon.FlutterDevice;
import io.flutter.utils.VmServiceListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlutterPerfView {
  public static final String TOOL_WINDOW_ID = "Flutter Performance";

  private static final Logger LOG = Logger.getInstance(FlutterPerfView.class);

  @NotNull
  private final Project myProject;

  private final Map<FlutterApp, PerfViewAppState> perAppViewState = new HashMap<>();

  public FlutterPerfView(@NotNull Project project) {
    myProject = project;
  }

  void initToolWindow(ToolWindow window) {
    if (window.isDisposed()) return;

    updateForEmptyContent(window);
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  private void updateForEmptyContent(ToolWindow toolWindow) {
    // There's a possible race here where the tool window gets disposed while we're displaying contents.
    if (toolWindow.isDisposed()) {
      //noinspection UnnecessaryReturnStatement
      return;
    }
  }

  void debugActive(@NotNull FlutterViewMessages.FlutterDebugEvent event) {
    final FlutterApp app = event.app;

    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    if (!(toolWindowManager instanceof ToolWindowManagerEx)) {
      return;
    }

    final ToolWindow toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_ID);
    if (toolWindow == null) {
      return;
    }

    addPerformanceViewContent(app, toolWindow);

    app.getVmService().addVmServiceListener(new VmServiceListenerAdapter() {
      @Override
      public void connectionOpened() {
        onAppChanged(app);
      }

      @Override
      public void connectionClosed() {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (toolWindow.isDisposed()) return;
          final ContentManager contentManager = toolWindow.getContentManager();
          onAppChanged(app);
          final PerfViewAppState state = perAppViewState.remove(app);
          if (state != null) {
            if (state.content != null) {
              contentManager.removeContent(state.content, true);
            }
            if (state.disposable != null) {
              state.disposable.dispose();
            }
          }
          if (perAppViewState.isEmpty()) {
            // No more applications are running.
            updateForEmptyContent(toolWindow);
          }
        });
      }
    });

    onAppChanged(app);
  }

  private void addPerformanceViewContent(FlutterApp app, ToolWindow toolWindow) {
    final ContentManager contentManager = toolWindow.getContentManager();
    final SimpleToolWindowPanel toolWindowPanel = new SimpleToolWindowPanel(true);

    final List<FlutterDevice> existingDevices = new ArrayList<>();
    for (FlutterApp otherApp : perAppViewState.keySet()) {
      existingDevices.add(otherApp.device());
    }

    final JPanel panelContainer = new JPanel(new BorderLayout());
    final Content content = contentManager.getFactory().createContent(null, app.device().getUniqueName(existingDevices), false);
    content.setComponent(panelContainer);
    content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
    content.setIcon(FlutterIcons.Phone);
    contentManager.addContent(content);

    final PerfViewAppState state = getOrCreateStateForApp(app);
    assert (state.content == null);
    state.content = content;

    final boolean debugConnectionAvailable = app.getLaunchMode().supportsDebugConnection();
    final boolean isInProfileMode = app.getMode().isProfiling() || app.getLaunchMode().isProfiling();

    // If the inspector is available (non-release mode), then show it.
    if (debugConnectionAvailable) {
      state.disposable = Disposer.newDisposable();

      final InspectorMemoryTab memoryTab = new InspectorMemoryTab(state.disposable, app);
      panelContainer.add(memoryTab, BorderLayout.CENTER);

      // If in profile mode, auto-open the performance tool window.
      if (isInProfileMode) {
        activateToolWindow();
      }
    }
    else {
      // Add a message about the inspector not being available in release mode.
      final JBLabel label = new JBLabel("Profiling is not available in release mode", SwingConstants.CENTER);
      label.setForeground(UIUtil.getLabelDisabledForeground());
      panelContainer.add(label, BorderLayout.CENTER);
    }
  }

  private void onAppChanged(FlutterApp app) {
    if (myProject.isDisposed()) {
      return;
    }

    final ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(TOOL_WINDOW_ID);
    if (toolWindow == null) {
      //noinspection UnnecessaryReturnStatement
      return;
    }
  }

  /**
   * Activate the tool window.
   */
  private void activateToolWindow() {
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);

    final ToolWindow toolWindow = toolWindowManager.getToolWindow(TOOL_WINDOW_ID);
    if (toolWindow.isVisible()) {
      return;
    }

    toolWindow.show(null);
  }

  private PerfViewAppState getStateForApp(FlutterApp app) {
    return perAppViewState.get(app);
  }

  private PerfViewAppState getOrCreateStateForApp(FlutterApp app) {
    return perAppViewState.computeIfAbsent(app, k -> new PerfViewAppState());
  }

  private static class PerfViewAppState {
    @Nullable Content content;

    @Nullable Disposable disposable;
  }
}
