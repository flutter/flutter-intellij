/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
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
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
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
import io.flutter.FlutterInitializer;
import io.flutter.FlutterUtils;
import io.flutter.devtools.DevToolsManager;
import io.flutter.inspector.InspectorService;
import io.flutter.run.FlutterDevice;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.AsyncUtils;
import io.flutter.utils.EventStream;
import io.flutter.utils.VmServiceListenerAdapter;
import io.flutter.vmService.ServiceExtensions;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.element.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static io.flutter.utils.AsyncUtils.whenCompleteUiThread;
import static io.flutter.vmService.ServiceExtensions.enableOnDeviceInspector;

@com.intellij.openapi.components.State(
  name = "FlutterView",
  storages = {@Storage("$WORKSPACE_FILE$")}
)
public class FlutterView implements PersistentStateComponent<FlutterViewState>, Disposable {

  private static final Logger LOG = Logger.getInstance(FlutterView.class);

  private static class PerAppState extends AppState {
    ArrayList<InspectorPanel> inspectorPanels = new ArrayList<>();
    boolean sendRestartNotificationOnNextFrame = false;

    public void dispose() {
      for (InspectorPanel panel : inspectorPanels) {
        Disposer.dispose(panel);
      }
    }
  }

  private Content emptyContent;

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

  private boolean disposed = false;

  public FlutterView(@NotNull Project project) {
    myProject = project;

    shouldAutoHorizontalScroll.listen(state::setShouldAutoScroll);
    highlightNodesShownInBothTrees.listen(state::setHighlightNodesShownInBothTrees);
  }

  @Override
  public void dispose() {
    disposed = true;
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

    updateForEmptyContent(window);
  }

  private DefaultActionGroup createToolbar(@NotNull ToolWindow toolWindow,
                                           @NotNull FlutterApp app,
                                           Disposable parentDisposable,
                                           InspectorService inspectorService) {
    final DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    final PerAppState state = getOrCreateStateForApp(app);

    if (inspectorService != null) {
      toolbarGroup.addSeparator();
      toolbarGroup.add(state.registerAction(new ForceRefreshAction(app, inspectorService)));
    }
    toolbarGroup.addSeparator();
    toolbarGroup.add(state.registerAction(new PerformanceOverlayAction(app)));
    toolbarGroup.addSeparator();
    toolbarGroup.add(state.registerAction(new DebugPaintAction(app)));
    toolbarGroup.add(state.registerAction(new ShowPaintBaselinesAction(app, true)));
    toolbarGroup.addSeparator();
    toolbarGroup.add(state.registerAction(new TimeDilationAction(app, true)));
    toolbarGroup.addSeparator();
    toolbarGroup.add(new TogglePlatformAction(getOrCreateStateForApp(app), app));

    final FlutterViewAction selectModeAction = state.registerAction(new ToggleSelectWidgetMode(app));
    final FlutterViewAction legacySelectModeAction = state.registerAction(new ToggleOnDeviceWidgetInspector(app));
    final FlutterViewAction currentExtension[] = { null };

    app.getVMServiceManager().hasServiceExtension(enableOnDeviceInspector.getExtension(), (hasExtension) -> {
      if (hasExtension) {
        FlutterViewAction nextExtension = hasExtension ? selectModeAction : legacySelectModeAction;
        if (!disposed && currentExtension[0] != nextExtension) {
          if (currentExtension[0] != null) {
            toolbarGroup.remove(currentExtension[0]);
          }
          toolbarGroup.add(nextExtension, Constraints.FIRST);
          currentExtension[0] = nextExtension;
        }
      }
    });

    return toolbarGroup;
  }

  private PerAppState getStateForApp(FlutterApp app) {
    return perAppViewState.get(app);
  }

  private PerAppState getOrCreateStateForApp(FlutterApp app) {
    return perAppViewState.computeIfAbsent(app, k -> new PerAppState());
  }

  private void addInspectorViewContent(FlutterApp app, @Nullable InspectorService inspectorService, ToolWindow toolWindow) {
    final ContentManager contentManager = toolWindow.getContentManager();
    final SimpleToolWindowPanel toolWindowPanel = new SimpleToolWindowPanel(true);
    final JBRunnerTabs runnerTabs = new JBRunnerTabs(myProject, ActionManager.getInstance(), IdeFocusManager.getInstance(myProject), this);
    runnerTabs.setSelectionChangeHandler(this::onTabSelectionChange);
    final JPanel tabContainer = new JPanel(new BorderLayout());

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

    final Content content = contentManager.getFactory().createContent(null, tabName, false);
    tabContainer.add(runnerTabs.getComponent(), BorderLayout.CENTER);
    content.setComponent(tabContainer);
    content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
    content.setIcon(FlutterIcons.Phone);
    contentManager.addContent(content);

    if (emptyContent != null) {
      contentManager.removeContent(emptyContent, true);
      emptyContent = null;
    }

    contentManager.setSelectedContent(content);

    final PerAppState state = getOrCreateStateForApp(app);
    assert (state.content == null);
    state.content = content;
    state.tabs = runnerTabs;

    final DefaultActionGroup toolbarGroup = createToolbar(toolWindow, app, runnerTabs, inspectorService);
    toolWindowPanel.setToolbar(ActionManager.getInstance().createActionToolbar("FlutterViewToolbar", toolbarGroup, true).getComponent());

    toolbarGroup.add(new OverflowAction(getOrCreateStateForApp(app), this, app));

    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("InspectorToolbar", toolbarGroup, true);
    final JComponent toolbarComponent = toolbar.getComponent();
    toolbarComponent.setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    tabContainer.add(toolbarComponent, BorderLayout.NORTH);

    final boolean debugConnectionAvailable = app.getLaunchMode().supportsDebugConnection();
    final boolean hasInspectorService = inspectorService != null;

    // If the inspector is available (non-release mode), then show it.
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
        addDisabledTab(WIDGET_TAB_LABEL, runnerTabs, toolbarGroup);
        addDisabledTab(RENDER_TAB_LABEL, runnerTabs, toolbarGroup);
      }
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

  private void addInspectorPanel(String displayName,
                                 JBRunnerTabs tabs,
                                 PerAppState state,
                                 InspectorService.FlutterTreeType treeType,
                                 FlutterApp app,
                                 InspectorService inspectorService,
                                 @NotNull ToolWindow toolWindow,
                                 DefaultActionGroup toolbarGroup,
                                 boolean selectedTab,
                                 boolean useSummaryTree) {
    final InspectorPanel inspectorPanel = new InspectorPanel(
      this,
      app,
      inspectorService,
      app::isSessionActive,
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
                              DefaultActionGroup toolbarGroup) {
    final JPanel panel = new JPanel(new BorderLayout());
    final JBLabel label = new JBLabel("Widget info not available in profile mode", SwingConstants.CENTER);
    label.setForeground(UIUtil.getLabelDisabledForeground());
    panel.add(label, BorderLayout.CENTER);

    final TabInfo tabInfo = new TabInfo(panel)
      .append(displayName, SimpleTextAttributes.GRAYED_ATTRIBUTES);
    runnerTabs.addTab(tabInfo);
  }

  /**
   * Called when a debug connection starts.
   */
  public void debugActive(@NotNull FlutterViewMessages.FlutterDebugEvent event) {
    final FlutterApp app = event.app;

    if (app.getMode().isProfiling() || app.getLaunchMode().isProfiling()) {
      ApplicationManager.getApplication().invokeLater(() -> debugActiveHelper(app, null));
    }
    else {
      whenCompleteUiThread(
        app.getFlutterDebugProcess().getInspectorService(),
        (InspectorService inspectorService, Throwable throwable) -> {
          // XXX remove
          // app.getFlutterDebugProcess().setInspectorService(inspectorService);
          if (throwable != null) {
            FlutterUtils.warn(LOG, throwable);
            return;
          }

          debugActiveHelper(app, inspectorService);
        });
    }
  }

  private void debugActiveHelper(FlutterApp app, @Nullable InspectorService inspectorService) {
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    if (!(toolWindowManager instanceof ToolWindowManagerEx)) {
      return;
    }

    final ToolWindow toolWindow = toolWindowManager.getToolWindow(FlutterView.TOOL_WINDOW_ID);
    if (toolWindow == null) {
      return;
    }

    if (toolWindow.isAvailable()) {
      updateToolWindowVisibility(toolWindow);
    }
    else {
      toolWindow.setAvailable(true, () -> {
        updateToolWindowVisibility(toolWindow);
      });
    }

    if (emptyContent != null) {
      final ContentManager contentManager = toolWindow.getContentManager();
      contentManager.removeContent(emptyContent, true);
      emptyContent = null;
    }

    toolWindow.setIcon(ExecutionUtil.getLiveIndicator(FlutterIcons.Flutter_13));

    listenForRenderTreeActivations(toolWindow);

    addInspectorViewContent(app, inspectorService, toolWindow);

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
          if (inspectorService != null) {
            Disposer.dispose(inspectorService);
          }

          if (toolWindow.isDisposed()) return;
          final ContentManager contentManager = toolWindow.getContentManager();
          onAppChanged(app);
          final PerAppState state = perAppViewState.remove(app);
          if (state != null) {
            if (state.content != null) {
              contentManager.removeContent(state.content, true);
            }
            state.dispose();
          }
          if (perAppViewState.isEmpty()) {
            // No more applications are running.
            updateForEmptyContent(toolWindow);
          }
        });
      }
    });

    onAppChanged(app);

    app.addStateListener(new FlutterApp.FlutterAppListener() {
      public void notifyAppRestarted() {
        // When we get a restart finished event, queue up a notification to the flutter view
        // actions. We don't notify right away because the new isolate can take a little
        // while to start up. We wait until we get the first frame event, which is
        // enough of an indication that the isolate and flutter framework are initialized
        // to where they can receive service calls (for example, calls to restore various
        // framework debugging settings).
        final PerAppState state = getStateForApp(app);
        if (state != null) {
          state.sendRestartNotificationOnNextFrame = true;
        }
      }
    });
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

  private static void listenForRenderTreeActivations(@NotNull ToolWindow toolWindow) {
    final ContentManager contentManager = toolWindow.getContentManager();
    contentManager.addContentManagerListener(new ContentManagerAdapter() {
      @Override
      public void selectionChanged(@NotNull ContentManagerEvent event) {
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

  private void updateToolWindowVisibility(ToolWindow flutterToolWindow) {
    if (flutterToolWindow.isVisible()) {
      return;
    }

    if (FlutterSettings.getInstance().isOpenInspectorOnAppLaunch()) {
      flutterToolWindow.show(null);
    }
  }
}

class FlutterViewDevToolsAction extends FlutterViewAction {
  FlutterViewDevToolsAction(@NotNull FlutterApp app) {
    super(app, "Open DevTools", "Open Dart DevTools", FlutterIcons.Dart_16);
  }

  @Override
  public void perform(AnActionEvent event) {
    if (app.isSessionActive()) {
      final String urlString = app.getConnector().getBrowserUrl();
      if (urlString == null) {
        return;
      }

      final DevToolsManager devToolsManager = DevToolsManager.getInstance(app.getProject());

      if (devToolsManager.hasInstalledDevTools()) {
        devToolsManager.openBrowserAndConnect(urlString);
      }
      else {
        final CompletableFuture<Boolean> result = devToolsManager.installDevTools();
        result.thenAccept(o -> devToolsManager.openBrowserAndConnect(urlString));
      }
    }
  }
}

class RepaintRainbowAction extends FlutterViewToggleableAction {
  RepaintRainbowAction(@NotNull FlutterApp app) {
    super(app, FlutterIcons.RepaintRainbow, ServiceExtensions.repaintRainbow);
  }
}

class ToggleSelectWidgetMode extends FlutterViewToggleableAction {
  ToggleSelectWidgetMode(@NotNull FlutterApp app) {
    super(app, AllIcons.General.Locate, ServiceExtensions.toggleSelectWidgetMode);
  }

  @Override
  protected void perform(AnActionEvent event) {
    super.perform(event);

    if (app.isSessionActive()) {
      // If toggling inspect mode on, bring the app's device to the foreground.
      if (isSelected()) {
        final FlutterDevice device = app.device();
        if (device != null) {
          device.bringToFront();
        }
      }
    }
  }

  @Override
  public void handleAppRestarted() {
    if (isSelected()) {
      setSelected(null, false);
    }
  }
}

class ToggleOnDeviceWidgetInspector extends FlutterViewToggleableAction {
  ToggleOnDeviceWidgetInspector(@NotNull FlutterApp app) {
    super(app, AllIcons.General.Locate, ServiceExtensions.toggleOnDeviceWidgetInspector);
  }

  @Override
  protected void perform(AnActionEvent event) {
    super.perform(event);

    if (app.isSessionActive()) {
      // If toggling inspect mode on, bring the app's device to the foreground.
      if (isSelected()) {
        final FlutterDevice device = app.device();
        if (device != null) {
          device.bringToFront();
        }
      }
    }
  }

  @Override
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

  @Override
  protected void perform(final AnActionEvent event) {
    if (app.isSessionActive()) {
      setEnabled(event, false);

      final CompletableFuture<?> future = inspectorService.forceRefresh();

      AsyncUtils.whenCompleteUiThread(future, (o, throwable) -> setEnabled(event, true));
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(app.isSessionActive() && enabled);
  }
}

class ShowDebugBannerAction extends FlutterViewToggleableAction {
  ShowDebugBannerAction(@NotNull FlutterApp app) {
    super(app, FlutterIcons.DebugBanner, ServiceExtensions.debugAllowBanner);
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

class OverflowAction extends ToolbarComboBoxAction implements RightAlignedToolbarAction {
  private final @NotNull FlutterApp app;
  private final DefaultActionGroup myActionGroup;

  public OverflowAction(@NotNull AppState appState, @NotNull FlutterView view, @NotNull FlutterApp app) {
    super();

    this.app = app;
    myActionGroup = createPopupActionGroup(appState, view, app);
  }

  @NotNull
  @Override
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    return myActionGroup;
  }

  @Override
  public final void update(AnActionEvent e) {
    e.getPresentation().setText("More Actions");
    e.getPresentation().setEnabled(app.isSessionActive());
  }

  private static DefaultActionGroup createPopupActionGroup(AppState appState, FlutterView view, FlutterApp app) {
    final DefaultActionGroup group = new DefaultActionGroup();

    group.add(appState.registerAction(new RepaintRainbowAction(app)));
    group.addSeparator();
    group.add(appState.registerAction(new ShowDebugBannerAction(app)));
    group.addSeparator();
    group.add(appState.registerAction(new AutoHorizontalScrollAction(app, view.shouldAutoHorizontalScroll)));
    group.add(appState.registerAction(new HighlightNodesShownInBothTrees(app, view.highlightNodesShownInBothTrees)));
    group.addSeparator();
    group.add(appState.registerAction(new FlutterViewDevToolsAction(app)));

    return group;
  }
}

class AppState {
  ArrayList<FlutterViewAction> flutterViewActions = new ArrayList<>();
  JBRunnerTabs tabs;
  Content content;

  FlutterViewAction registerAction(FlutterViewAction action) {
    flutterViewActions.add(action);
    return action;
  }
}
