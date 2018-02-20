/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.inspector.InspectorService;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.run.daemon.FlutterDevice;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.VmServiceListenerAdapter;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.element.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

// TODO(devoncarew): Display an fps graph.

// TODO(devoncarew): Ensure all actions in this class send analytics.

@com.intellij.openapi.components.State(
  name = "FlutterView",
  storages = {@Storage("$WORKSPACE_FILE$")}
)


public class FlutterView implements PersistentStateComponent<FlutterViewState>, Disposable {

  private static class PerAppState {
    ArrayList<FlutterViewAction> flutterViewActions = new ArrayList<>();
    ArrayList<InspectorPanel> inspectorPanels = new ArrayList<>();
    ArrayList<Content> contents = new ArrayList<>();
    boolean sendRestartNotificationOnNextFrame = false;
  }

  public static final String TOOL_WINDOW_ID = "Flutter Inspector";

  @NotNull
  private final FlutterViewState state = new FlutterViewState();

  @NotNull
  private final Project myProject;

  private String restoreToolWindowId;

  private final Map<FlutterApp, PerAppState> perAppViewState = new HashMap<>();

  public FlutterView(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void dispose() {
  }

  @NotNull
  @Override
  public FlutterViewState getState() {
    return this.state;
  }

  @Override
  public void loadState(FlutterViewState state) {
    this.state.copyFrom(state);
  }

  public void initToolWindow(ToolWindow window) {
    window.setToHideOnEmptyContent(true);
    // TODO(jacobr): add a message explaining the empty contents if the user
    // manually opens the window when there is not yet a running app.
  }

  private DefaultActionGroup createToolbar(@NotNull ToolWindow toolWindow, FlutterApp app) {
    final DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.add(registerAction(new ToggleInspectModeAction(app)));
    toolbarGroup.addSeparator();
    toolbarGroup.add(registerAction(new DebugDrawAction(app)));
    toolbarGroup.add(registerAction(new TogglePlatformAction(app)));
    toolbarGroup.add(registerAction(new PerformanceOverlayAction(app)));
    toolbarGroup.addSeparator();
    toolbarGroup.add(registerAction(new OpenTimelineViewAction(app)));
    toolbarGroup.add(registerAction(new OpenObservatoryAction(app)));
    toolbarGroup.addSeparator();
    toolbarGroup.add(new OverflowActionsAction(this, app));
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
    PerAppState state = perAppViewState.get(app);
    if (state == null) {
      state = new PerAppState();
      perAppViewState.put(app, state);
    }
    return state;
  }

  private void addInspectorPanel(String displayName,
                                 InspectorService.FlutterTreeType treeType,
                                 FlutterApp flutterApp,
                                 @NotNull ToolWindow toolWindow,
                                 DefaultActionGroup toolbarGroup,
                                 boolean selectedContent) {
    {
      final ContentManager contentManager = toolWindow.getContentManager();
      final ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
      final Content content = contentFactory.createContent(null, displayName, false);
      content.setCloseable(true);
      final SimpleToolWindowPanel windowPanel = new SimpleToolWindowPanel(true, true);
      content.setComponent(windowPanel);

      final InspectorPanel inspectorPanel = new InspectorPanel(this, flutterApp, flutterApp::isSessionActive, treeType);
      windowPanel.setContent(inspectorPanel);
      windowPanel.setToolbar(ActionManager.getInstance().createActionToolbar("FlutterViewToolbar", toolbarGroup, true).getComponent());
      contentManager.addContent(content);
      PerAppState state = getOrCreateStateForApp(flutterApp);
      state.contents.add(content);
      if (selectedContent) {
        contentManager.setSelectedContent(content);
      }

      state.inspectorPanels.add(inspectorPanel);
    }
  }

  /**
   * Called when a debug connection starts.
   */
  public void debugActive(@NotNull FlutterViewMessages.FlutterDebugEvent event) {
    if (FlutterSettings.getInstance().isOpenInspectorOnAppLaunch()) {
      autoActivateToolWindow();
    }

    final FlutterApp app = event.app;
    assert (app != null);

    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    if (!(toolWindowManager instanceof ToolWindowManagerEx)) {
      return;
    }

    final ToolWindow toolWindow = toolWindowManager.getToolWindow(FlutterView.TOOL_WINDOW_ID);
    if (toolWindow == null) {
      return;
    }

    DefaultActionGroup toolbarGroup = createToolbar(toolWindow, app);

    addInspectorPanel("Widgets", InspectorService.FlutterTreeType.widget, app, toolWindow, toolbarGroup, true);
    addInspectorPanel("Render Tree", InspectorService.FlutterTreeType.renderObject, app, toolWindow, toolbarGroup, false);

    event.vmService.addVmServiceListener(new VmServiceListenerAdapter() {
      @Override
      public void connectionOpened() {
        onAppChanged(app);
      }

      @Override
      public void received(String streamId, Event event) {
        // Note: we depend here on the streamListen("Extension") call in InspectorService.
        if (StringUtil.equals(streamId, VmService.EXTENSION_STREAM_ID)) {
          if (StringUtil.equals("Flutter.Frame", event.getExtensionKind())) {
            handleFlutterFrame(app);
          }
        }
      }

      @Override
      public void connectionClosed() {
        ApplicationManager.getApplication().invokeLater(() -> {
          final ContentManager contentManager = toolWindow.getContentManager();
          onAppChanged(app);
          final PerAppState state = perAppViewState.remove(app);
          if (state != null) {
            for (Content content : state.contents) {
              contentManager.removeContent(content, true);
            }
          }
          if (perAppViewState.isEmpty()) {
            // No more applications are running.
            restorePreviousToolWindow();
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
      toolWindow.setIcon(FlutterIcons.Flutter_13);
      notifyActionsAppStopped(app);
    }
    else {
      toolWindow.setIcon(ExecutionUtil.getLiveIndicator(FlutterIcons.Flutter_13));
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

    restoreToolWindowId = null;

    final ToolWindow flutterToolWindow = toolWindowManager.getToolWindow(FlutterView.TOOL_WINDOW_ID);
    if (flutterToolWindow.isVisible()) {
      return;
    }

    final ToolWindowManagerEx toolWindowManagerEx = (ToolWindowManagerEx)toolWindowManager;

    for (String id : toolWindowManagerEx.getIdsOn(flutterToolWindow.getAnchor())) {
      final ToolWindow toolWindow = toolWindowManagerEx.getToolWindow(id);
      if (toolWindow.isVisible()) {
        restoreToolWindowId = id;
      }
    }

    flutterToolWindow.show(null);
  }

  private void restorePreviousToolWindow() {
    if (restoreToolWindowId == null) {
      return;
    }

    ApplicationManager.getApplication().invokeLater(() -> {
      final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
      final ToolWindow flutterToolWindow = toolWindowManager.getToolWindow(FlutterView.TOOL_WINDOW_ID);

      // Show this view iff the flutter view is the one still visible.
      if (flutterToolWindow.isVisible()) {
        final ToolWindow toolWindow = toolWindowManager.getToolWindow(restoreToolWindowId);
        toolWindow.show(null);
      }

      restoreToolWindowId = null;
    });
  }
}

class DebugDrawAction extends FlutterViewToggleableAction {
  DebugDrawAction(@NotNull FlutterApp app) {
    super(app, FlutterBundle.message("flutter.view.debugPaint.text"), FlutterBundle.message("flutter.view.debugPaint.description"),
          AllIcons.General.TbShown);
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
  public void actionPerformed(AnActionEvent event) {
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
  public void actionPerformed(AnActionEvent event) {
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

  TogglePlatformAction(@NotNull FlutterApp app) {
    super(app, FlutterBundle.message("flutter.view.togglePlatform.text"), FlutterBundle.message("flutter.view.togglePlatform.description"),
          AllIcons.RunConfigurations.Application);
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
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
  }

  protected void perform(AnActionEvent event) {
    if (app != null && app.isSessionActive()) {
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

class HideSlowBannerAction extends FlutterViewToggleableAction {
  HideSlowBannerAction(@NotNull FlutterApp app) {
    super(app, "Hide Slow Mode Banner");
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

class ShowPaintBaselinesAction extends FlutterViewToggleableAction {
  ShowPaintBaselinesAction(@NotNull FlutterApp app) {
    super(app, "Show Paint Baselines");
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

class OverflowActionsAction extends AnAction implements CustomComponentAction {
  private final @NotNull FlutterApp app;
  private final DefaultActionGroup myActionGroup;

  public OverflowActionsAction(@NotNull FlutterView view, FlutterApp app) {
    super("Additional actions", null, AllIcons.General.Gear);

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

    group.add(view.registerAction(new ShowPaintBaselinesAction(app)));
    group.addSeparator();
    group.add(view.registerAction(new RepaintRainbowAction(app)));
    group.add(view.registerAction(new TimeDilationAction(app)));
    group.addSeparator();
    group.add(view.registerAction(new HideSlowBannerAction(app)));

    return group;
  }
}
