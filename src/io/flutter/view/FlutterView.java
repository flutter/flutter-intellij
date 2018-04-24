/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.icons.AllIcons;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.ActiveRunnable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.ui.UIUtil;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.FlutterInitializer;
import io.flutter.inspector.InspectorService;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.run.daemon.FlutterDevice;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.AsyncUtils;
import io.flutter.utils.EventStream;
import io.flutter.utils.StreamSubscription;
import io.flutter.utils.VmServiceListenerAdapter;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.element.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static io.flutter.utils.AsyncUtils.whenCompleteUiThread;

@com.intellij.openapi.components.State(
  name = "FlutterView",
  storages = {@Storage("$WORKSPACE_FILE$")}
)
public class FlutterView implements PersistentStateComponent<FlutterViewState>, Disposable {

  private static final Logger LOG = Logger.getInstance(FlutterView.class);

  private static class PerAppState {
    ArrayList<FlutterViewAction> flutterViewActions = new ArrayList<>();
    ArrayList<InspectorPanel> inspectorPanels = new ArrayList<>();
    JBRunnerTabs tabs;
    Content content;
    boolean sendRestartNotificationOnNextFrame = false;
  }

  public static final String TOOL_WINDOW_ID = "Flutter Inspector";

  public static final String WIDGET_TAB_LABEL = "Widgets";
  public static final String RENDER_TAB_LABEL = "Render Tree";
  public static final String PERFORMANCE_TAB_LABEL = "Performance";

  protected final EventStream<Boolean> shouldAutoHorizontalScroll = new EventStream<>(FlutterViewState.AUTO_SCROLL_DEFAULT);
  protected final EventStream<Boolean> highlightNodesShownInBothTrees =
    new EventStream<>(FlutterViewState.HIGHLIGHT_NODES_SHOWN_IN_BOTH_TREES_DEFAULT);


  @NotNull
  private final FlutterViewState state = new FlutterViewState();

  @NotNull
  private final Project myProject;

  private final Map<FlutterApp, PerAppState> perAppViewState = new HashMap<>();

  private Content emptyContent;

  public FlutterView(@NotNull Project project) {
    myProject = project;

    shouldAutoHorizontalScroll.listen(state::setShouldAutoScroll);
    highlightNodesShownInBothTrees.listen(state::setHighlightNodesShownInBothTrees);
  }

  @Override
  public void dispose() {
    Disposer.dispose(this);
  }

  @NotNull
  @Override
  public FlutterViewState getState() {
    return state;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @Override
  public void loadState(@NotNull FlutterViewState state) {
    this.state.copyFrom(state);

    shouldAutoHorizontalScroll.setValue(this.state.getShouldAutoScroll());
    highlightNodesShownInBothTrees.setValue(this.state.getHighlightNodesShownInBothTrees());
  }

  void initToolWindow(ToolWindow window) {
    if (window.isDisposed()) return;

    // Add a feedback button.
    if (window instanceof ToolWindowEx) {
      final AnAction sendFeedbackAction = new AnAction("Send Feedback", "Send Feedback", FlutterIcons.Feedback) {
        @Override
        public void actionPerformed(AnActionEvent event) {
          BrowserUtil.browse("https://goo.gl/WrMB43");
        }
      };

      ((ToolWindowEx)window).setTitleActions(sendFeedbackAction);
    }

    displayEmptyContent(window);
  }

  private DefaultActionGroup createToolbar(@NotNull ToolWindow toolWindow,
                                           @NotNull FlutterApp app,
                                           Disposable parentDisposable,
                                           InspectorService inspectorService) {
    final DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.add(registerAction(new ToggleInspectModeAction(app)));
    if (inspectorService != null) {
      toolbarGroup.addSeparator();
      toolbarGroup.add(registerAction(new ForceRefreshAction(app, inspectorService)));
    }
    toolbarGroup.addSeparator();
    toolbarGroup.add(registerAction(new DebugDrawAction(app)));
    toolbarGroup.add(registerAction(new TogglePlatformAction(app)));
    toolbarGroup.add(registerAction(new PerformanceOverlayAction(app)));
    toolbarGroup.addSeparator();
    toolbarGroup.add(registerAction(new OpenTimelineViewAction(app)));
    toolbarGroup.add(registerAction(new OpenObservatoryAction(app)));

    return toolbarGroup;
  }

  FlutterViewAction registerAction(FlutterViewAction action) {
    getOrCreateStateForApp(action.app).flutterViewActions.add(action);
    return action;
  }

  private PerAppState getStateForApp(FlutterApp app) {
    return perAppViewState.get(app);
  }

  private PerAppState getOrCreateStateForApp(FlutterApp app) {
    return perAppViewState.computeIfAbsent(app, k -> new PerAppState());
  }

  private void addInspector(FlutterApp app, @Nullable InspectorService inspectorService, ToolWindow toolWindow) {
    final ContentManager contentManager = toolWindow.getContentManager();
    final SimpleToolWindowPanel toolWindowPanel = new SimpleToolWindowPanel(true);
    final JBRunnerTabs runnerTabs = new JBRunnerTabs(myProject, ActionManager.getInstance(), null, this);
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
    final PerAppState state = getOrCreateStateForApp(app);
    assert (state.content == null);
    state.content = content;
    state.tabs = runnerTabs;

    final DefaultActionGroup toolbarGroup = createToolbar(toolWindow, app, runnerTabs, inspectorService);
    toolWindowPanel.setToolbar(ActionManager.getInstance().createActionToolbar("FlutterViewToolbar", toolbarGroup, true).getComponent());

    toolbarGroup.add(new OverflowAction(this, app));

    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("InspectorToolbar", toolbarGroup, true);
    final JComponent toolbarComponent = toolbar.getComponent();
    toolbarComponent.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    tabContainer.add(toolbarComponent, BorderLayout.NORTH);

    final boolean debugConnectionAvailable = app.getLaunchMode().supportsDebugConnection();
    final boolean hasInspectorService = inspectorService != null;

    // If the inspector is available (non-profile mode), then show it.
    if (debugConnectionAvailable) {
      if (hasInspectorService) {
        final boolean detailsSummaryViewSupported = inspectorService.isDetailsSummaryViewSupported();
        addInspectorPanel(WIDGET_TAB_LABEL, runnerTabs, state, InspectorService.FlutterTreeType.widget, app, inspectorService, toolWindow,
                          toolbarGroup, true, detailsSummaryViewSupported);
        addInspectorPanel(RENDER_TAB_LABEL, runnerTabs, state, InspectorService.FlutterTreeType.renderObject, app, inspectorService,
                          toolWindow,
                          toolbarGroup, false, false);
      }
      else {
        // If in profile mode, add disabled tabs for the inspector.
        addDisabledTab(WIDGET_TAB_LABEL, runnerTabs, app, toolbarGroup);
        addDisabledTab(RENDER_TAB_LABEL, runnerTabs, app, toolbarGroup);
      }

      addPerformanceTab(runnerTabs, app, !hasInspectorService);
      runnerTabs.setSelectionChangeHandler(this::onTabSelectionChange);
    }
    else {
      // Add a message about the inspector not being available in release mode.
      final JBLabel label = new JBLabel("Inspector not available in release mode", SwingConstants.CENTER);
      label.setForeground(UIUtil.getLabelDisabledForeground());
      tabContainer.add(label, BorderLayout.CENTER);
    }
  }

  private ActionCallback onTabSelectionChange(TabInfo info, boolean requestFocus, @NotNull ActiveRunnable doChangeSelection) {
    if (info.getComponent() instanceof InspectorTabPanel) {
      final InspectorTabPanel panel = (InspectorTabPanel)info.getComponent();
      panel.setVisibleToUser(true);
    }

    final TabInfo previous = info.getPreviousSelection();

    // Track analytics for explicit inspector tab selections.
    // (The initial selection will have no previous, so we filter that out.)
    if (previous != null) {
      FlutterInitializer.getAnalytics().sendScreenView(
        FlutterView.TOOL_WINDOW_ID.toLowerCase() + "/" + info.getText().toLowerCase());
    }

    if (previous != null && previous.getComponent() instanceof InspectorTabPanel) {
      final InspectorTabPanel panel = (InspectorTabPanel)previous.getComponent();
      panel.setVisibleToUser(false);
    }
    return doChangeSelection.run();
  }

  public void switchToRenderTree(FlutterApp app) {
    final PerAppState state = perAppViewState.get(app);
    for (TabInfo tabInfo : state.tabs.getTabs()) {
      if (tabInfo.getComponent() instanceof InspectorPanel) {
        final InspectorPanel panel = (InspectorPanel)tabInfo.getComponent();
        if (panel.getTreeType() == InspectorService.FlutterTreeType.renderObject) {
          state.tabs.select(tabInfo, true);
          return;
        }
      }
    }
  }

  private void addInspectorPanel(String displayName,
                                 JBRunnerTabs tabs,
                                 PerAppState state,
                                 InspectorService.FlutterTreeType treeType,
                                 FlutterApp flutterApp,
                                 InspectorService inspectorService,
                                 @NotNull ToolWindow toolWindow,
                                 DefaultActionGroup toolbarGroup,
                                 boolean selectedTab,
                                 boolean useSummaryTree) {
    final InspectorPanel inspectorPanel = new InspectorPanel(
      this,
      flutterApp,
      inspectorService,
      flutterApp::isSessionActive,
      treeType,
      useSummaryTree,
      // TODO(jacobr): support the summary tree view for the RenderObject
      // tree instead of forcing the legacy view for the RenderObject tree.
      treeType != InspectorService.FlutterTreeType.widget || !inspectorService.isDetailsSummaryViewSupported(),
      shouldAutoHorizontalScroll,
      highlightNodesShownInBothTrees
    );
    final TabInfo tabInfo = new TabInfo(inspectorPanel)
      .append(displayName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    tabs.addTab(tabInfo);
    state.inspectorPanels.add(inspectorPanel);
    if (selectedTab) {
      tabs.select(tabInfo, false);
    }
  }

  private void addDisabledTab(String displayName,
                              JBRunnerTabs runnerTabs,
                              FlutterApp app,
                              DefaultActionGroup toolbarGroup) {
    final JPanel panel = new JPanel(new BorderLayout());
    final JBLabel label = new JBLabel("Widget info not available in profile mode", SwingConstants.CENTER);
    label.setForeground(UIUtil.getLabelDisabledForeground());
    panel.add(label, BorderLayout.CENTER);

    final TabInfo tabInfo = new TabInfo(panel)
      .append(displayName, SimpleTextAttributes.GRAYED_ATTRIBUTES);
    runnerTabs.addTab(tabInfo);
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

    if (!selectedTab) {
      assert app.getPerfService() != null;
      app.getPerfService().pausePolling();
    }
  }

  /**
   * Called when a debug connection starts.
   */
  public void debugActive(@NotNull FlutterViewMessages.FlutterDebugEvent event) {
    final FlutterApp app = event.app;

    if (app.getMode().isProfiling() || app.getLaunchMode().isProfiling()) {
      ApplicationManager.getApplication().invokeLater(() -> {
        debugActiveHelper(app, null);
      });
    }
    else {
      whenCompleteUiThread(
        InspectorService.create(app, app.getFlutterDebugProcess(), app.getVmService()),
        (InspectorService inspectorService, Throwable throwable) -> {
          if (throwable != null) {
            LOG.warn(throwable);
            return;
          }
          debugActiveHelper(app, inspectorService);
        });
    }
  }

  private void debugActiveHelper(@NotNull FlutterApp app, @Nullable InspectorService inspectorService) {
    if (FlutterSettings.getInstance().isOpenInspectorOnAppLaunch()) {
      autoActivateToolWindow();
    }

    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    if (!(toolWindowManager instanceof ToolWindowManagerEx)) {
      return;
    }

    final ToolWindow toolWindow = toolWindowManager.getToolWindow(FlutterView.TOOL_WINDOW_ID);
    if (toolWindow == null) {
      return;
    }

    if (isDisplayingEmptyContent()) {
      removeEmptyContent(toolWindow);
    }

    listenForRenderTreeActivations(toolWindow);

    addInspector(app, inspectorService, toolWindow);

    app.getVmService().addVmServiceListener(new VmServiceListenerAdapter() {
      @Override
      public void connectionOpened() {
        onAppChanged(app);
      }

      @Override
      public void received(String streamId, Event event) {
        if (StringUtil.equals(streamId, VmService.EXTENSION_STREAM_ID)) {
          if (StringUtil.equals("Flutter.Frame", event.getExtensionKind())) {
            handleFlutterFrame(app);
          }
        }
      }

      @Override
      public void connectionClosed() {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (toolWindow.isDisposed()) return;
          final ContentManager contentManager = toolWindow.getContentManager();
          onAppChanged(app);
          final PerAppState state = perAppViewState.remove(app);
          if (state != null && state.content != null) {
            contentManager.removeContent(state.content, true);
          }
          if (perAppViewState.isEmpty()) {
            // No more applications are running.
            displayEmptyContent(toolWindow);
          }
        });
      }
    });

    onAppChanged(app);

    app.addStateListener(new FlutterApp.FlutterAppListener() {
      public void notifyAppRestarted() {
        // When we get a restart finishes, queue up a notification to the flutter view
        // actions. We don't notify right away because the new isolate can take a little
        // while to start up. We wait until we get the first frame event, which is
        // enough of an indication that the isolate and flutter framework are initialized
        // enough to receive service calls (for example, calls to restore various framework
        // debugging settings).
        final PerAppState state = getStateForApp(app);
        if (state != null) {
          state.sendRestartNotificationOnNextFrame = true;
        }
      }
    });
  }

  private void displayEmptyContent(ToolWindow toolWindow) {
    // There's a possible race here where the tool window gets disposed while we're displaying contents.
    if (toolWindow.isDisposed()) {
      return;
    }

    // Display a 'No running applications' message.
    final ContentManager contentManager = toolWindow.getContentManager();
    final JPanel panel = new JPanel(new BorderLayout());
    final JLabel label = new JLabel("No running applications", SwingConstants.CENTER);
    label.setForeground(UIUtil.getLabelDisabledForeground());
    panel.add(label, BorderLayout.CENTER);
    emptyContent = contentManager.getFactory().createContent(panel, null, false);
    contentManager.addContent(emptyContent);

    toolWindow.setIcon(FlutterIcons.Flutter_13);
  }

  private boolean isDisplayingEmptyContent() {
    return emptyContent != null;
  }

  private void removeEmptyContent(ToolWindow toolWindow) {
    final ContentManager contentManager = toolWindow.getContentManager();
    contentManager.removeContent(emptyContent, true);

    toolWindow.setIcon(ExecutionUtil.getLiveIndicator(FlutterIcons.Flutter_13));

    emptyContent = null;
  }

  private static void listenForRenderTreeActivations(@NotNull ToolWindow toolWindow) {
    final ContentManager contentManager = toolWindow.getContentManager();
    contentManager.addContentManagerListener(new ContentManagerAdapter() {
      @Override
      public void selectionChanged(ContentManagerEvent event) {
        final ContentManagerEvent.ContentOperation operation = event.getOperation();
        if (operation == ContentManagerEvent.ContentOperation.add) {
          final String name = event.getContent().getTabName();
          if (Objects.equals(name, RENDER_TAB_LABEL)) {
            FlutterInitializer.getAnalytics().sendEvent("inspector", "renderTreeSelected");
          }
          else if (Objects.equals(name, WIDGET_TAB_LABEL)) {
            FlutterInitializer.getAnalytics().sendEvent("inspector", "widgetTreeSelected");
          }
        }
      }
    });
  }

  private void handleFlutterFrame(FlutterApp app) {
    final PerAppState state = getStateForApp(app);
    if (state != null && state.sendRestartNotificationOnNextFrame) {
      state.sendRestartNotificationOnNextFrame = false;
      notifyActionsOnRestart(app);
    }
  }

  private void notifyActionsAppStarted(FlutterApp app) {
    final PerAppState state = getStateForApp(app);
    if (state == null) {
      return;
    }
    for (FlutterViewAction action : state.flutterViewActions) {
      action.handleAppStarted();
    }
  }

  private void notifyActionsOnRestart(FlutterApp app) {
    final PerAppState state = getStateForApp(app);
    if (state == null) {
      return;
    }
    for (FlutterViewAction action : state.flutterViewActions) {
      action.handleAppRestarted();
    }
  }

  private void notifyActionsAppStopped(FlutterApp app) {
    final PerAppState state = getStateForApp(app);
    if (state == null) {
      return;
    }
    state.sendRestartNotificationOnNextFrame = false;
  }

  private void onAppChanged(FlutterApp app) {
    if (myProject.isDisposed()) {
      return;
    }

    final ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(TOOL_WINDOW_ID);
    if (toolWindow == null) {
      return;
    }

    if (perAppViewState.isEmpty()) {
      notifyActionsAppStopped(app);
    }
    else {
      notifyActionsAppStarted(app);
    }

    final PerAppState state = getStateForApp(app);
    if (state != null) {
      for (InspectorPanel inspectorPanel : state.inspectorPanels) {
        inspectorPanel.onAppChanged();
      }
    }
  }

  /**
   * Activate the tool window; on app termination, restore any previously active tool window.
   */
  private void autoActivateToolWindow() {
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    if (!(toolWindowManager instanceof ToolWindowManagerEx)) {
      return;
    }

    final ToolWindow flutterToolWindow = toolWindowManager.getToolWindow(FlutterView.TOOL_WINDOW_ID);
    if (flutterToolWindow.isVisible()) {
      return;
    }

    flutterToolWindow.show(null);
  }
}

class DebugDrawAction extends FlutterViewToggleableAction {
  DebugDrawAction(@NotNull FlutterApp app) {
    super(app, FlutterBundle.message("flutter.view.debugPaint.text"), FlutterBundle.message("flutter.view.debugPaint.description"),
          AllIcons.General.TbShown);

    setExtensionCommand("ext.flutter.debugPaint");
  }

  protected void perform(AnActionEvent event) {
    if (app.isSessionActive()) {
      app.callBooleanExtension("ext.flutter.debugPaint", isSelected());
    }
  }

  public void handleAppStarted() {
    handleAppRestarted();
  }

  public void handleAppRestarted() {
    if (isSelected()) {
      perform(null);
    }
  }
}

class PerformanceOverlayAction extends FlutterViewToggleableAction {
  PerformanceOverlayAction(@NotNull FlutterApp app) {
    super(app, "Toggle Performance Overlay", "Toggle Performance Overlay", AllIcons.Modules.Library);

    setExtensionCommand("ext.flutter.showPerformanceOverlay");
  }

  protected void perform(@Nullable AnActionEvent event) {
    if (app.isSessionActive()) {
      app.callBooleanExtension("ext.flutter.showPerformanceOverlay", isSelected());
    }
  }

  public void handleAppStarted() {
    handleAppRestarted();
  }

  public void handleAppRestarted() {
    if (isSelected()) {
      perform(null);
    }
  }
}

class OpenObservatoryAction extends FlutterViewAction {
  OpenObservatoryAction(@NotNull FlutterApp app) {
    super(app, FlutterBundle.message("open.observatory.action.text"), FlutterBundle.message("open.observatory.action.description"),
          FlutterIcons.OpenObservatory);
  }

  @Override
  public void perform(AnActionEvent event) {
    if (app.isSessionActive()) {
      final String url = app.getConnector().getBrowserUrl();
      if (url != null) {
        BrowserLauncher.getInstance().browse(url, null);
      }
    }
  }
}

class OpenTimelineViewAction extends FlutterViewAction {
  OpenTimelineViewAction(@NotNull FlutterApp app) {
    super(app, "Open Timeline View", "Open Timeline View", FlutterIcons.OpenTimeline);
  }

  @Override
  public void perform(AnActionEvent event) {
    if (app.isSessionActive()) {
      final String url = app.getConnector().getBrowserUrl();
      if (url != null) {
        BrowserLauncher.getInstance().browse(url + "/#/timeline-dashboard", null);
      }
    }
  }
}

class TogglePlatformAction extends FlutterViewAction {
  private Boolean isCurrentlyAndroid;
  CompletableFuture<Boolean> cachedHasExtensionFuture;
  private StreamSubscription<Boolean> subscription;

  TogglePlatformAction(@NotNull FlutterApp app) {
    super(app, FlutterBundle.message("flutter.view.togglePlatform.text"),
          FlutterBundle.message("flutter.view.togglePlatform.description"),
          AllIcons.RunConfigurations.Application);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (!app.isSessionActive()) {
      if (subscription != null) {
        subscription.dispose();
        subscription = null;
      }
      e.getPresentation().setEnabled(false);
      return;
    }

    if (subscription == null) {
      subscription = app.hasServiceExtension("ext.flutter.platformOverride", (enabled) -> {
        e.getPresentation().setEnabled(app.isSessionActive() && enabled);
      });
    }
  }

  @Override
  public void perform(AnActionEvent event) {
    if (app.isSessionActive()) {
      app.togglePlatform().thenAccept(isAndroid -> {
        if (isAndroid == null) {
          return;
        }

        app.togglePlatform(!isAndroid).thenAccept(isNowAndroid -> {
          if (app.getConsole() != null && isNowAndroid != null) {
            isCurrentlyAndroid = isNowAndroid;

            app.getConsole().print(
              FlutterBundle.message("flutter.view.togglePlatform.output",
                                    isNowAndroid ? "Android" : "iOS"),
              ConsoleViewContentType.SYSTEM_OUTPUT);
          }
        });
      });
    }
  }

  public void handleAppRestarted() {
    if (isCurrentlyAndroid != null) {
      app.togglePlatform(isCurrentlyAndroid);
    }
  }
}

class RepaintRainbowAction extends FlutterViewToggleableAction {
  RepaintRainbowAction(@NotNull FlutterApp app) {
    super(app, "Enable Repaint Rainbow");

    setExtensionCommand("ext.flutter.repaintRainbow");
  }

  protected void perform(@Nullable AnActionEvent event) {
    if (app.isSessionActive()) {
      app.callBooleanExtension("ext.flutter.repaintRainbow", isSelected());
    }
  }

  public void handleAppStarted() {
    handleAppRestarted();
  }

  public void handleAppRestarted() {
    if (isSelected()) {
      perform(null);
    }
  }
}

class TimeDilationAction extends FlutterViewToggleableAction {
  TimeDilationAction(@NotNull FlutterApp app) {
    super(app, "Enable Slow Animations");

    setExtensionCommand("ext.flutter.timeDilation");
  }

  protected void perform(@Nullable AnActionEvent event) {
    final Map<String, Object> params = new HashMap<>();
    params.put("timeDilation", isSelected() ? 5.0 : 1.0);
    if (app.isSessionActive()) {
      app.callServiceExtension("ext.flutter.timeDilation", params);
    }
  }

  public void handleAppRestarted() {
    if (isSelected()) {
      perform(null);
    }
  }
}

class ToggleInspectModeAction extends FlutterViewToggleableAction {
  ToggleInspectModeAction(@NotNull FlutterApp app) {
    super(app, "Toggle Select Widget Mode", "Toggle Select Widget Mode", AllIcons.General.LocateHover);

    setExtensionCommand("ext.flutter.debugWidgetInspector");
  }

  protected void perform(AnActionEvent event) {
    if (app.isSessionActive()) {
      app.callBooleanExtension("ext.flutter.debugWidgetInspector", isSelected());

      // If toggling inspect mode on, bring all devices to the foreground.
      // TODO(jacobr): consider only bringing the device for the currently open inspector TAB.
      if (isSelected()) {
        final FlutterDevice device = app.device();
        if (device != null) {
          device.bringToFront();
        }
      }
    }
  }

  public void handleAppRestarted() {
    if (isSelected()) {
      setSelected(null, false);
    }
  }
}

class ForceRefreshAction extends FlutterViewAction {
  final @NotNull InspectorService inspectorService;

  private boolean enabled = true;

  ForceRefreshAction(@NotNull FlutterApp app, @NotNull InspectorService inspectorService) {
    super(app, "Refresh Widget Info", "Refresh Widget Info", AllIcons.Actions.ForceRefresh);

    this.inspectorService = inspectorService;
  }

  private void setEnabled(AnActionEvent event, boolean enabled) {
    this.enabled = enabled;

    update(event);
  }

  protected void perform(final AnActionEvent event) {
    if (app.isSessionActive()) {
      setEnabled(event, false);

      final CompletableFuture<?> future = inspectorService.forceRefresh();

      AsyncUtils.whenCompleteUiThread(future, (o, throwable) -> {
        setEnabled(event, true);
      });
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(app.isSessionActive() && enabled);
  }
}

class HideSlowBannerAction extends FlutterViewToggleableAction {
  HideSlowBannerAction(@NotNull FlutterApp app) {
    super(app, "Hide Debug Mode Banner");

    setExtensionCommand("ext.flutter.debugAllowBanner");
  }

  @Override
  protected void perform(@Nullable AnActionEvent event) {
    if (app.isSessionActive()) {
      app.callBooleanExtension("ext.flutter.debugAllowBanner", !isSelected());
    }
  }

  public void handleAppStarted() {
    handleAppRestarted();
  }

  public void handleAppRestarted() {
    if (isSelected()) {
      perform(null);
    }
  }
}

class AutoHorizontalScrollAction extends FlutterViewLocalToggleableAction {
  AutoHorizontalScrollAction(@NotNull FlutterApp app, EventStream<Boolean> value) {
    super(app, "Auto horizontal scroll", value);
  }
}

class HighlightNodesShownInBothTrees extends FlutterViewLocalToggleableAction {
  HighlightNodesShownInBothTrees(@NotNull FlutterApp app, EventStream<Boolean> value) {
    super(app, "Highlight nodes displayed in both trees", value);
  }
}

class ShowPaintBaselinesAction extends FlutterViewToggleableAction {
  ShowPaintBaselinesAction(@NotNull FlutterApp app) {
    super(app, "Show Paint Baselines");

    setExtensionCommand("ext.flutter.debugPaintBaselinesEnabled");
  }

  @Override
  protected void perform(@Nullable AnActionEvent event) {
    if (app.isSessionActive()) {
      app.callBooleanExtension("ext.flutter.debugPaintBaselinesEnabled", isSelected());
    }
  }

  public void handleAppStarted() {
    handleAppRestarted();
  }

  public void handleAppRestarted() {
    if (isSelected()) {
      perform(null);
    }
  }
}

class OverflowAction extends AnAction implements RightAlignedToolbarAction {
  private final @NotNull FlutterApp app;
  private final DefaultActionGroup myActionGroup;

  public OverflowAction(@NotNull FlutterView view, @NotNull FlutterApp app) {
    super("Additional actions", null, AllIcons.General.Gear);

    this.app = app;

    myActionGroup = createPopupActionGroup(view, app);
  }

  ActionButton getActionButton() {
    final Presentation presentation = getTemplatePresentation().clone();
    final ActionButton actionButton = new ActionButton(
      this,
      presentation,
      ActionPlaces.UNKNOWN,
      ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
    );
    presentation.putClientProperty("button", actionButton);
    return actionButton;
  }

  @Override
  public final void update(AnActionEvent e) {
    e.getPresentation().setEnabled(app.isSessionActive());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    JComponent component = (JComponent)presentation.getClientProperty("button");
    if (component == null && e.getInputEvent().getSource() instanceof JComponent) {
      component = (JComponent)e.getInputEvent().getSource();
    }
    if (component == null) {
      return;
    }
    final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(
      ActionPlaces.UNKNOWN,
      myActionGroup);
    popupMenu.getComponent().show(component, component.getWidth(), 0);
  }

  private static DefaultActionGroup createPopupActionGroup(FlutterView view, FlutterApp app) {
    final DefaultActionGroup group = new DefaultActionGroup();

    group.add(view.registerAction(new ShowPaintBaselinesAction(app)));
    group.addSeparator();
    group.add(view.registerAction(new RepaintRainbowAction(app)));
    group.add(view.registerAction(new TimeDilationAction(app)));
    group.addSeparator();
    group.add(view.registerAction(new HideSlowBannerAction(app)));
    group.addSeparator();
    group.add(view.registerAction(new AutoHorizontalScrollAction(app, view.shouldAutoHorizontalScroll)));
    group.add(view.registerAction(new HighlightNodesShownInBothTrees(app, view.highlightNodesShownInBothTrees)));

    return group;
  }
}

class ObservatoryActionGroup extends AnAction implements CustomComponentAction {
  private final @NotNull FlutterApp app;
  private final DefaultActionGroup myActionGroup;

  public ObservatoryActionGroup(@NotNull FlutterView view, @NotNull FlutterApp app) {
    super("Observatory actions", null, FlutterIcons.OpenObservatoryGroup);

    this.app = app;

    myActionGroup = createPopupActionGroup(view, app);
  }

  @Override
  public final void update(AnActionEvent e) {
    e.getPresentation().setEnabled(app.isSessionActive());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final JComponent button = (JComponent)presentation.getClientProperty("button");
    if (button == null) {
      return;
    }
    final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(
      ActionPlaces.UNKNOWN,
      myActionGroup);
    popupMenu.getComponent().show(button, button.getWidth(), 0);
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    final ActionButton button = new ActionButton(
      this,
      presentation,
      ActionPlaces.UNKNOWN,
      ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE
    );
    presentation.putClientProperty("button", button);
    return button;
  }

  private static DefaultActionGroup createPopupActionGroup(FlutterView view, FlutterApp app) {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(view.registerAction(new OpenTimelineViewAction(app)));
    group.add(view.registerAction(new OpenObservatoryAction(app)));
    return group;
  }
}

