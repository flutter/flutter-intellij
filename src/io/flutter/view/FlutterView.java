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
import com.intellij.openapi.util.Computable;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO(devoncarew): Display an fps graph.

// TODO(devoncarew): Ensure all actions in this class send analytics.

@com.intellij.openapi.components.State(
  name = "FlutterView",
  storages = {@Storage("$WORKSPACE_FILE$")}
)
public class FlutterView implements PersistentStateComponent<FlutterViewState>, Disposable {
  public static final String TOOL_WINDOW_ID = "Flutter Inspector";

  @NotNull
  private final FlutterViewState state = new FlutterViewState();

  @NotNull
  private final Project myProject;

  private String restoreToolWindowId;

  @Nullable
  FlutterApp app;

  private final List<FlutterViewAction> flutterViewActions = new ArrayList<>();

  private final ArrayList<InspectorPanel> inspectorPanels = new ArrayList<>();

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

  public void initToolWindow(@NotNull ToolWindow toolWindow) {
    final ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();

    final DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.add(registerAction(new ToggleInspectModeAction(this)));
    toolbarGroup.addSeparator();
    toolbarGroup.add(registerAction(new DebugDrawAction(this)));
    toolbarGroup.add(registerAction(new TogglePlatformAction(this)));
    toolbarGroup.add(registerAction(new PerformanceOverlayAction(this)));
    toolbarGroup.addSeparator();
    toolbarGroup.add(registerAction(new OpenTimelineViewAction(this)));
    toolbarGroup.add(registerAction(new OpenObservatoryAction(this)));
    toolbarGroup.addSeparator();
    toolbarGroup.add(new OverflowActionsAction(this));

    addInspectorPanel("Widgets", InspectorService.FlutterTreeType.widget, toolWindow, toolbarGroup, true);
    addInspectorPanel("Render Tree", InspectorService.FlutterTreeType.renderObject, toolWindow, toolbarGroup, false);
  }

  FlutterViewAction registerAction(FlutterViewAction action) {
    flutterViewActions.add(action);
    return action;
  }

  private void addInspectorPanel(String displayName,
                                 InspectorService.FlutterTreeType treeType,
                                 @NotNull ToolWindow toolWindow,
                                 DefaultActionGroup toolbarGroup,
                                 boolean selectedContent) {
    {
      final ContentManager contentManager = toolWindow.getContentManager();
      final ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
      final Computable<Boolean> isSessionActive = () -> {
        final FlutterApp app = getFlutterApp();
        return app != null && app.isStarted() && app.getFlutterDebugProcess().getVmConnected() &&
               !app.getFlutterDebugProcess().getSession().isStopped();
      };

      final Content content = contentFactory.createContent(null, displayName, false);
      content.setCloseable(true);
      final SimpleToolWindowPanel windowPanel = new SimpleToolWindowPanel(true, true);
      content.setComponent(windowPanel);

      final InspectorPanel inspectorPanel = new InspectorPanel(this, isSessionActive, treeType);
      windowPanel.setContent(inspectorPanel);
      windowPanel.setToolbar(ActionManager.getInstance().createActionToolbar("FlutterViewToolbar", toolbarGroup, true).getComponent());

      contentManager.addContent(content);
      if (selectedContent) {
        contentManager.setSelectedContent(content);
      }

      inspectorPanels.add(inspectorPanel);
    }
    //renderObjectWindowPanel.setToolbar(ActionManager.getInstance().createActionToolbar("FlutterViewToolbar", toolbarGroup, true).getComponent());
  }

  /**
   * Called when a debug connection starts.
   */
  public void debugActive(@NotNull FlutterViewMessages.FlutterDebugEvent event) {
    if (FlutterSettings.getInstance().isOpenInspectorOnAppLaunch()) {
      autoActivateToolWindow();
    }

    this.app = event.app;

    event.vmService.addVmServiceListener(new VmServiceListenerAdapter() {
      @Override
      public void connectionOpened() {
        onAppChanged();
      }

      @Override
      public void received(String streamId, Event event) {
        // Note: we depend here on the streamListen("Extension") call in InspectorService.
        if (StringUtil.equals(streamId, VmService.EXTENSION_STREAM_ID)) {
          if (StringUtil.equals("Flutter.Frame", event.getExtensionKind())) {
            handleFlutterFrame();
          }
        }
      }

      @Override
      public void connectionClosed() {
        FlutterView.this.app = null;
        onAppChanged();
        restorePreviousToolWindow();
      }
    });

    onAppChanged();

    if (this.app != null) {
      this.app.addStateListener(new FlutterApp.FlutterAppListener() {
        public void notifyAppRestarted() {
          // When we get a restart finishes, queue up a notification to the flutter view
          // actions. We don't notify right away because the new isolate can take a little
          // while to start up. We wait until we get the first frame event, which is
          // enough of an indication that the isolate and flutter framework are initialized
          // enough to receive service calls (for example, calls to restore various framework
          // debugging settings).
          sendRestartNotificationOnNextFrame = true;
        }
      });
    }
  }

  private boolean sendRestartNotificationOnNextFrame = false;

  private void handleFlutterFrame() {
    if (sendRestartNotificationOnNextFrame) {
      sendRestartNotificationOnNextFrame = false;

      notifyActionsOnRestart();
    }
  }

  private void notifyActionsAppStarted() {
    for (FlutterViewAction action : flutterViewActions) {
      action.handleAppStarted();
    }
  }

  private void notifyActionsOnRestart() {
    for (FlutterViewAction action : flutterViewActions) {
      action.handleAppRestarted();
    }
  }

  private void notifyActionsAppStopped() {
    sendRestartNotificationOnNextFrame = false;

    for (FlutterViewAction action : flutterViewActions) {
      action.handleAppStopped();
    }
  }

  @Nullable
  FlutterApp getFlutterApp() {
    return app;
  }

  private void onAppChanged() {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myProject.isDisposed()) {
        return;
      }

      final ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(TOOL_WINDOW_ID);
      if (toolWindow == null) {
        return;
      }

      if (app == null) {
        toolWindow.setIcon(FlutterIcons.Flutter_13);
        notifyActionsAppStopped();
      }
      else {
        toolWindow.setIcon(ExecutionUtil.getLiveIndicator(FlutterIcons.Flutter_13));
        notifyActionsAppStarted();
      }

      for (InspectorPanel inspectorPanel : inspectorPanels) {
        inspectorPanel.onAppChanged();
        inspectorPanel.setEnabled(app != null);
      }
    });
  }

  private boolean hasFlutterApp() {
    return getFlutterApp() != null;
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
  DebugDrawAction(@NotNull FlutterView view) {
    super(view, FlutterBundle.message("flutter.view.debugPaint.text"), FlutterBundle.message("flutter.view.debugPaint.description"),
          AllIcons.General.TbShown);
  }

  protected void perform(AnActionEvent event) {
    assert (view.getFlutterApp() != null);
    view.getFlutterApp().callBooleanExtension("ext.flutter.debugPaint", isSelected());
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
  PerformanceOverlayAction(@NotNull FlutterView view) {
    super(view, "Toggle Performance Overlay", "Toggle Performance Overlay", AllIcons.Modules.Library);
  }

  protected void perform(@Nullable AnActionEvent event) {
    assert (view.getFlutterApp() != null);
    view.getFlutterApp().callBooleanExtension("ext.flutter.showPerformanceOverlay", isSelected());
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
  OpenObservatoryAction(@NotNull FlutterView view) {
    super(view, FlutterBundle.message("open.observatory.action.text"), FlutterBundle.message("open.observatory.action.description"),
          FlutterIcons.OpenObservatory);
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    final FlutterApp app = view.getFlutterApp();
    if (app == null) {
      return;
    }

    final String url = app.getConnector().getBrowserUrl();
    if (url != null) {
      BrowserLauncher.getInstance().browse(url, null);
    }
  }
}

class OpenTimelineViewAction extends FlutterViewAction {
  OpenTimelineViewAction(@NotNull FlutterView view) {
    super(view, "Open Timeline View", "Open Timeline View", FlutterIcons.OpenTimeline);
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    final FlutterApp app = view.getFlutterApp();
    if (app == null) {
      return;
    }

    final String url = app.getConnector().getBrowserUrl();
    if (url != null) {
      BrowserLauncher.getInstance().browse(url + "/#/timeline-dashboard", null);
    }
  }
}

class TogglePlatformAction extends FlutterViewAction {
  private Boolean isCurrentlyAndroid;

  TogglePlatformAction(@NotNull FlutterView view) {
    super(view, FlutterBundle.message("flutter.view.togglePlatform.text"), FlutterBundle.message("flutter.view.togglePlatform.description"),
          AllIcons.RunConfigurations.Application);
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    final FlutterApp app = view.getFlutterApp();
    if (app == null) {
      return;
    }

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

  public void handleAppRestarted() {
    final FlutterApp app = view.getFlutterApp();
    if (app == null) {
      return;
    }

    if (isCurrentlyAndroid != null) {
      app.togglePlatform(isCurrentlyAndroid);
    }
  }

  public void handleAppStopped() {
    isCurrentlyAndroid = null;
  }
}

class RepaintRainbowAction extends FlutterViewToggleableAction {
  RepaintRainbowAction(@NotNull FlutterView view) {
    super(view, "Enable Repaint Rainbow");
  }

  protected void perform(@Nullable AnActionEvent event) {
    assert (view.getFlutterApp() != null);
    view.getFlutterApp().callBooleanExtension("ext.flutter.repaintRainbow", isSelected());
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
  TimeDilationAction(@NotNull FlutterView view) {
    super(view, "Enable Slow Animations");
  }

  protected void perform(@Nullable AnActionEvent event) {
    final Map<String, Object> params = new HashMap<>();
    params.put("timeDilation", isSelected() ? 5.0 : 1.0);
    assert (view.getFlutterApp() != null);
    view.getFlutterApp().callServiceExtension("ext.flutter.timeDilation", params);
  }

  public void handleAppRestarted() {
    if (isSelected()) {
      perform(null);
    }
  }

  public void handleAppStopped() {
    if (isSelected()) {
      setSelected(null, false);
    }
  }
}

class ToggleInspectModeAction extends FlutterViewToggleableAction {
  ToggleInspectModeAction(@NotNull FlutterView view) {
    super(view, "Toggle Select Widget Mode", "Toggle Select Widget Mode", AllIcons.General.LocateHover);
  }

  protected void perform(AnActionEvent event) {
    assert (view.getFlutterApp() != null);
    view.getFlutterApp().callBooleanExtension("ext.flutter.debugWidgetInspector", isSelected());

    // If toggling inspect mode on, bring any device to the foreground.
    if (isSelected()) {
      final FlutterDevice device = getDevice();
      if (device != null) {
        device.bringToFront();
      }
    }
  }

  public void handleAppRestarted() {
    if (isSelected()) {
      setSelected(null, false);
    }
  }

  public void handleAppStopped() {
    if (isSelected()) {
      setSelected(null, false);
    }
  }
}

class HideSlowBannerAction extends FlutterViewToggleableAction {
  HideSlowBannerAction(@NotNull FlutterView view) {
    super(view, "Hide Slow Mode Banner");
  }

  @Override
  protected void perform(@Nullable AnActionEvent event) {
    assert (view.getFlutterApp() != null);
    view.getFlutterApp().callBooleanExtension("ext.flutter.debugAllowBanner", !isSelected());
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
  ShowPaintBaselinesAction(@NotNull FlutterView view) {
    super(view, "Show Paint Baselines");
  }

  @Override
  protected void perform(@Nullable AnActionEvent event) {
    assert (view.getFlutterApp() != null);
    view.getFlutterApp().callBooleanExtension("ext.flutter.debugPaintBaselinesEnabled", isSelected());
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
  private final @NotNull FlutterView view;
  private final DefaultActionGroup myActionGroup;

  public OverflowActionsAction(@NotNull FlutterView view) {
    super("Additional actions", null, AllIcons.General.Gear);

    this.view = view;

    myActionGroup = createPopupActionGroup(view);
  }

  @Override
  public final void update(AnActionEvent e) {
    final boolean hasFlutterApp = view.getFlutterApp() != null;
    e.getPresentation().setEnabled(hasFlutterApp);
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

  private static DefaultActionGroup createPopupActionGroup(FlutterView view) {
    final DefaultActionGroup group = new DefaultActionGroup();

    group.add(view.registerAction(new ShowPaintBaselinesAction(view)));
    group.addSeparator();
    group.add(view.registerAction(new RepaintRainbowAction(view)));
    group.add(view.registerAction(new TimeDilationAction(view)));
    group.addSeparator();
    group.add(view.registerAction(new HideSlowBannerAction(view)));

    return group;
  }
}
