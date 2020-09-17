/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.performance;

import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.FlutterIcons;
import io.flutter.devtools.DevToolsManager;
import io.flutter.run.FlutterDevice;
import io.flutter.run.FlutterLaunchMode;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.VmServiceListenerAdapter;
import io.flutter.view.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlutterPerformanceView implements Disposable {
  public static final String TOOL_WINDOW_ID = "Flutter Performance";

  private static final Logger LOG = Logger.getInstance(FlutterPerformanceView.class);

  @NotNull
  private final Project myProject;

  private final Map<FlutterApp, PerfViewAppState> perAppViewState = new HashMap<>();

  private Content emptyContent;

  public FlutterPerformanceView(@NotNull Project project) {
    myProject = project;
  }

  void initToolWindow(ToolWindow window) {
    if (window.isDisposed()) return;

    updateForEmptyContent(window);
  }

  @Override
  public void dispose() {
    Disposer.dispose(this);
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  private void updateForEmptyContent(ToolWindow toolWindow) {
    // There's a possible race here where the tool window gets disposed while we're displaying contents.
    if (toolWindow.isDisposed()) {
      return;
    }

    toolWindow.setIcon(FlutterIcons.Flutter_13);

    // Display a 'No running applications' message.
    final ContentManager contentManager = toolWindow.getContentManager();
    final JPanel panel = new JPanel(new BorderLayout());
    final JBLabel label = new JBLabel("No running applications", SwingConstants.CENTER);
    label.setForeground(UIUtil.getLabelDisabledForeground());
    panel.add(label, BorderLayout.CENTER);
    emptyContent = contentManager.getFactory().createContent(panel, null, false);
    contentManager.addContent(emptyContent);
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

    if (!toolWindow.isAvailable()) {
      toolWindow.setAvailable(true, null);
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
              Disposer.dispose(state.disposable);
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

    final String tabName;
    final FlutterDevice device = app.device();
    if (device == null) {
      tabName = app.getProject().getName();
    }
    else {
      final List<FlutterDevice> existingDevices = new ArrayList<>();
      for (FlutterApp otherApp : perAppViewState.keySet()) {
        existingDevices.add(otherApp.device());
      }
      tabName = device.getUniqueName(existingDevices);
    }

    // mainContentPanel contains the toolbar, perfViewsPanel, and the footer
    final JPanel mainContentPanel = new JPanel(new BorderLayout());
    final Content content = contentManager.getFactory().createContent(null, tabName, false);
    content.setComponent(mainContentPanel);
    content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
    content.setIcon(FlutterIcons.Phone);
    contentManager.addContent(content);

    // perfViewsPanel contains the three performance views
    final JComponent perfViewsPanel = Box.createVerticalBox();
    perfViewsPanel.setBorder(JBUI.Borders.empty(0, 3));
    mainContentPanel.add(perfViewsPanel, BorderLayout.CENTER);

    if (emptyContent != null) {
      contentManager.removeContent(emptyContent, true);
      emptyContent = null;
    }

    toolWindow.setIcon(ExecutionUtil.getLiveIndicator(FlutterIcons.Flutter_13));

    final PerfViewAppState state = getOrCreateStateForApp(app);
    assert (state.content == null);
    state.content = content;

    final DefaultActionGroup toolbarGroup = createToolbar(toolWindow, app, this);
    toolWindowPanel.setToolbar(ActionManager.getInstance().createActionToolbar(
      "FlutterPerfViewToolbar", toolbarGroup, true).getComponent());

    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("PerformanceToolbar", toolbarGroup, true);
    final JComponent toolbarComponent = toolbar.getComponent();
    toolbarComponent.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    mainContentPanel.add(toolbarComponent, BorderLayout.NORTH);

    // devtools link and run mode footer
    final JPanel footer = new JPanel(new BorderLayout());
    footer.setBorder(new CompoundBorder(
      IdeBorderFactory.createBorder(SideBorder.TOP), JBUI.Borders.empty(5, 7)));

    final JLabel runModeLabel = new JBLabel("Run mode: " + app.getLaunchMode());
    if (app.getLaunchMode() == FlutterLaunchMode.DEBUG) {
      runModeLabel.setIcon(AllIcons.General.BalloonInformation);
      runModeLabel.setToolTipText("Note: debug mode frame rendering times are not indicative of release mode performance");
    }

    final LinkLabel<String> openDevtools = new LinkLabel<>("Open DevTools...", null);
    openDevtools.setListener((linkLabel, data) -> {
      final DevToolsManager devToolsManager = DevToolsManager.getInstance(app.getProject());
      devToolsManager.openToScreen(app, null);
    }, null);

    footer.add(runModeLabel, BorderLayout.WEST);
    footer.add(openDevtools, BorderLayout.EAST);

    mainContentPanel.add(footer, BorderLayout.SOUTH);

    final boolean debugConnectionAvailable = app.getLaunchMode().supportsDebugConnection();
    final boolean isInProfileMode = app.getMode().isProfiling() || app.getLaunchMode().isProfiling();

    // If the inspector is available (non-release mode), then show it.
    if (debugConnectionAvailable) {
      state.disposable = Disposer.newDisposable();

      // Create the three FPS, memory, and widget recount areas.
      final PerfFPSPanel fpsPanel = new PerfFPSPanel(app, this);
      perfViewsPanel.add(fpsPanel);

      final PerfMemoryPanel memoryPanel = new PerfMemoryPanel(app, this);
      perfViewsPanel.add(memoryPanel);

      final PerfWidgetRebuildsPanel widgetRebuildsPanel = new PerfWidgetRebuildsPanel(app, this);
      perfViewsPanel.add(widgetRebuildsPanel);

      // If in profile mode, auto-open the performance tool window.
      if (isInProfileMode) {
        activateToolWindow();
      }
    }
    else {
      // Add a message about the inspector not being available in release mode.
      final JBLabel label = new JBLabel("Profiling is not available in release mode", SwingConstants.CENTER);
      label.setForeground(UIUtil.getLabelDisabledForeground());
      mainContentPanel.add(label, BorderLayout.CENTER);
    }
  }

  private DefaultActionGroup createToolbar(@NotNull ToolWindow toolWindow,
                                           @NotNull FlutterApp app,
                                           Disposable parentDisposable) {
    final DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.add(registerAction(new PerformanceOverlayAction(app)));
    toolbarGroup.addSeparator();
    toolbarGroup.add(registerAction(new DebugPaintAction(app)));
    toolbarGroup.add(registerAction(new ShowPaintBaselinesAction(app, true)));
    toolbarGroup.addSeparator();
    toolbarGroup.add(registerAction(new TimeDilationAction(app, true)));

    return toolbarGroup;
  }

  FlutterViewAction registerAction(FlutterViewAction action) {
    getOrCreateStateForApp(action.app).flutterViewActions.add(action);
    return action;
  }

  public void showForApp(@NotNull FlutterApp app) {
    final PerfViewAppState appState = perAppViewState.get(app);
    if (appState != null) {
      final ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(TOOL_WINDOW_ID);
      toolWindow.getContentManager().setSelectedContent(appState.content);
    }
  }

  public void showForAppRebuildCounts(@NotNull FlutterApp app) {
    final PerfViewAppState appState = perAppViewState.get(app);
    if (appState != null) {
      final ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(TOOL_WINDOW_ID);

      toolWindow.getContentManager().setSelectedContent(appState.content);
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
    if (toolWindow == null || toolWindow.isVisible()) {
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
}

class PerfViewAppState {
  ArrayList<FlutterViewAction> flutterViewActions = new ArrayList<>();
  @Nullable Disposable disposable;
  Content content;

  FlutterViewAction registerAction(FlutterViewAction action) {
    flutterViewActions.add(action);
    return action;
  }
}
