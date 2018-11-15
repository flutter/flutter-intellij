/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.ActiveRunnable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.ui.UIUtil;
import icons.FlutterIcons;
import io.flutter.FlutterInitializer;
import io.flutter.FlutterUtils;
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

public class FlutterPerfView implements Disposable {
  public static final String TOOL_WINDOW_ID = "Flutter Performance";
  public static final String PERFORMANCE_TAB_LABEL = "Performance";
  public static final String MEMORY_TAB_LABEL = "Memory";

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
    final JBRunnerTabs runnerTabs = new JBRunnerTabs(myProject, ActionManager.getInstance(), null, this);
    runnerTabs.setSelectionChangeHandler(this::onTabSelectionChange);

    final List<FlutterDevice> existingDevices = new ArrayList<>();
    for (FlutterApp otherApp : perAppViewState.keySet()) {
      existingDevices.add(otherApp.device());
    }

    final JPanel tabContainer = new JPanel(new BorderLayout());
    final Content content = contentManager.getFactory().createContent(null, app.device().getUniqueName(existingDevices), false);
    tabContainer.add(runnerTabs.getComponent(), BorderLayout.CENTER);
    content.setComponent(tabContainer);
    content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
    content.setIcon(FlutterIcons.Phone);
    contentManager.addContent(content);

    final PerfViewAppState state = getOrCreateStateForApp(app);
    assert (state.content == null);
    state.content = content;
    state.tabs = runnerTabs;

    final DefaultActionGroup toolbarGroup = createToolbar(toolWindow, app, runnerTabs);
    toolWindowPanel
      .setToolbar(ActionManager.getInstance().createActionToolbar("FlutterPerfViewToolbar", toolbarGroup, true).getComponent());

    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("PerformanceToolbar", toolbarGroup, true);
    final JComponent toolbarComponent = toolbar.getComponent();
    toolbarComponent.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    tabContainer.add(toolbarComponent, BorderLayout.NORTH);

    final boolean debugConnectionAvailable = app.getLaunchMode().supportsDebugConnection();
    final boolean isInProfileMode = app.getMode().isProfiling() || app.getLaunchMode().isProfiling();

    // If the inspector is available (non-release mode), then show it.
    if (debugConnectionAvailable) {
      state.disposable = Disposer.newDisposable();

      addPerformanceTab(runnerTabs, app, true);

      if (FlutterUtils.isAndroidStudio()) {
        addMemoryTab(runnerTabs, app, false, state);
      }

      // If in profile mode, auto-open the performance tool window.
      if (isInProfileMode) {
        activateToolWindow();
      }
    }
    else {
      // Add a message about the inspector not being available in release mode.
      final JBLabel label = new JBLabel("Profiling is not available in release mode", SwingConstants.CENTER);
      label.setForeground(UIUtil.getLabelDisabledForeground());
      tabContainer.add(label, BorderLayout.CENTER);
    }
  }

  private DefaultActionGroup createToolbar(@NotNull ToolWindow toolWindow,
                                           @NotNull FlutterApp app,
                                           Disposable parentDisposable) {
    final DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.add(registerAction(new DebugPaintAction(app)));
    toolbarGroup.add(registerAction(new TogglePlatformAction(app)));
    toolbarGroup.add(registerAction(new PerformanceOverlayAction(app)));
    toolbarGroup.addSeparator();
    toolbarGroup.add(registerAction(new ShowPaintBaselinesAction(app, true)));
    toolbarGroup.add(registerAction(new TimeDilationAction(app, true)));

    return toolbarGroup;
  }

  FlutterViewAction registerAction(FlutterViewAction action) {
    getOrCreateStateForApp(action.app).flutterViewActions.add(action);
    return action;
  }

  private void addPerformanceTab(JBRunnerTabs runnerTabs,
                                 FlutterApp app,
                                 boolean selectedTab) {
    final InspectorPerfTab perfTab = new InspectorPerfTab(runnerTabs, app);
    final TabInfo tabInfo = new TabInfo(perfTab)
      .append(PERFORMANCE_TAB_LABEL, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    runnerTabs.addTab(tabInfo);
    if (selectedTab) {
      runnerTabs.select(tabInfo, false);
    }
  }

  private void addMemoryTab(JBRunnerTabs runnerTabs,
                            FlutterApp app,
                            boolean selectedTab,
                            PerfViewAppState state) {
    final InspectorMemoryTab memoryTab = new InspectorMemoryTab(state.disposable, app);
    final TabInfo tabInfo = new TabInfo(memoryTab)
      .append(MEMORY_TAB_LABEL, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    runnerTabs.addTab(tabInfo);
    if (selectedTab) {
      runnerTabs.select(tabInfo, false);
    }
  }

  private ActionCallback onTabSelectionChange(TabInfo info, boolean requestFocus, @NotNull ActiveRunnable doChangeSelection) {
    if (info.getComponent() instanceof InspectorTabPanel) {
      final InspectorTabPanel panel = (InspectorTabPanel)info.getComponent();
      panel.setVisibleToUser(true);
    }

    final TabInfo previous = info.getPreviousSelection();

    // Track analytics for explicit tab selections.
    // (The initial selection will have no previous, so we filter that out.)
    if (previous != null) {
      FlutterInitializer.getAnalytics().sendScreenView(
        FlutterPerfView.TOOL_WINDOW_ID.toLowerCase() + "/" + info.getText().toLowerCase());
    }

    if (previous != null && previous.getComponent() instanceof InspectorTabPanel) {
      final InspectorTabPanel panel = (InspectorTabPanel)previous.getComponent();
      panel.setVisibleToUser(false);
    }
    return doChangeSelection.run();
  }

  public InspectorPerfTab showPerfTab(@NotNull FlutterApp app) {
    PerfViewAppState appState = perAppViewState.get(app);
    if (appState != null) {
      final ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(TOOL_WINDOW_ID);

      toolWindow.getContentManager().setSelectedContent(appState.content);
      for (TabInfo tabInfo : appState.tabs.getTabs()) {
        if (tabInfo.getComponent() instanceof InspectorPerfTab) {
          appState.tabs.select(tabInfo, true);
          return (InspectorPerfTab)tabInfo.getComponent();
        }
      }
    }
    return null;
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
    JBRunnerTabs tabs;
    ArrayList<FlutterViewAction> flutterViewActions = new ArrayList<>();
  }
}
