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
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.inspector.InspectorService;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.run.daemon.FlutterDevice;
import io.flutter.utils.VmServiceListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

// TODO(devoncarew): Display an fps graph.
// TODO(devoncarew): Have a pref setting for opening when starting a debug session.
// TODO(devoncarew): The toggle settings can get out of sync with the runtime, after full
//                   restarts or on new app launches. We need to query the framework and /
//                   or listen to change events from the framework.

@com.intellij.openapi.components.State(
  name = "FlutterView",
  storages = {@Storage("$WORKSPACE_FILE$")}
)
public class FlutterView implements PersistentStateComponent<FlutterView.State>, Disposable {
  public static final String TOOL_WINDOW_ID = "Flutter";

  @NotNull
  private FlutterView.State state = new FlutterView.State();

  @NotNull
  private final Project myProject;

  @Nullable
  FlutterApp app;

  private final ArrayList<InspectorPanel> inspectorPanels = new ArrayList<>();

  public FlutterView(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void dispose() {
  }

  @NotNull
  @Override
  public FlutterView.State getState() {
    return this.state;
  }

  @Override
  public void loadState(FlutterView.State state) {
    this.state = state;
  }

  public void initToolWindow(@NotNull ToolWindow toolWindow) {
    final ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();

    final DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.add(new DebugDrawAction(this));
    toolbarGroup.add(new ToggleInspectModeAction(this));
    toolbarGroup.add(new TogglePlatformAction(this));
    toolbarGroup.addSeparator();
    toolbarGroup.add(new OpenObservatoryAction(this));
    toolbarGroup.addSeparator();
    toolbarGroup.add(new OverflowActionsAction(this));

    addInspectorPanel("Widgets", InspectorService.FlutterTreeType.widget, toolWindow, toolbarGroup, true);
    addInspectorPanel("Render Tree", InspectorService.FlutterTreeType.renderObject, toolWindow, toolbarGroup, false);
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
    this.app = event.app;

    event.vmService.addVmServiceListener(new VmServiceListenerAdapter() {
      @Override
      public void connectionOpened() {
        onAppChanged();
      }

      @Override
      public void connectionClosed() {
        FlutterView.this.app = null;
        onAppChanged();
      }
    });

    onAppChanged();
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
      if (toolWindow == null) return;

      if (app == null) {
        toolWindow.setIcon(FlutterIcons.Flutter_13);
      }
      else {
        toolWindow.setIcon(ExecutionUtil.getLiveIndicator(FlutterIcons.Flutter_13));
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
   * State for the view.
   */
  class State {
  }
}

class DebugDrawAction extends AbstractToggleableAction {
  DebugDrawAction(@NotNull FlutterView view) {
    super(view, FlutterBundle.message("flutter.view.debugPaint.text"), FlutterBundle.message("flutter.view.debugPaint.description"),
          AllIcons.General.TbShown);
  }

  protected void perform(AnActionEvent event) {
    assert (view.getFlutterApp() != null);
    view.getFlutterApp().callBooleanExtension("ext.flutter.debugPaint", isSelected(event));
  }
}

class PerformanceOverlayAction extends AbstractToggleableAction {
  PerformanceOverlayAction(@NotNull FlutterView view) {
    super(view, "Show Performance Overlay");
  }

  protected void perform(AnActionEvent event) {
    assert (view.getFlutterApp() != null);
    view.getFlutterApp().callBooleanExtension("ext.flutter.showPerformanceOverlay", isSelected(event));
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

class TogglePlatformAction extends FlutterViewAction {
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
          app.getConsole().print(
            FlutterBundle.message("flutter.view.togglePlatform.output",
                                  isNowAndroid ? "Android" : "iOS"),
            ConsoleViewContentType.SYSTEM_OUTPUT);
        }
      });
    });
  }
}

class RepaintRainbowAction extends AbstractToggleableAction {
  RepaintRainbowAction(@NotNull FlutterView view) {
    super(view, "Enable Repaint Rainbow");
  }

  protected void perform(AnActionEvent event) {
    assert (view.getFlutterApp() != null);
    view.getFlutterApp().callBooleanExtension("ext.flutter.repaintRainbow", isSelected(event));
  }
}

class TimeDilationAction extends AbstractToggleableAction {
  TimeDilationAction(@NotNull FlutterView view) {
    super(view, "Enable Slow Animations");
  }

  protected void perform(AnActionEvent event) {
    final Map<String, Object> params = new HashMap<>();
    params.put("timeDilation", isSelected(event) ? 5.0 : 1.0);
    assert (view.getFlutterApp() != null);
    view.getFlutterApp().callServiceExtension("ext.flutter.timeDilation", params);
  }
}

class ToggleInspectModeAction extends AbstractToggleableAction {
  ToggleInspectModeAction(@NotNull FlutterView view) {
    super(view, "Toggle Inspect Mode", "Toggle Inspect Mode", AllIcons.General.LocateHover);
  }

  protected void perform(AnActionEvent event) {
    assert (view.getFlutterApp() != null);
    view.getFlutterApp().callBooleanExtension("ext.flutter.debugWidgetInspector", isSelected(event));

    // If toggling inspect mode on, bring any device to the foreground.
    if (isSelected(event)) {
      final FlutterDevice device = getDevice();
      if (device != null) {
        device.bringToFront();
      }
    }
  }
}

class HideSlowBannerAction extends AbstractToggleableAction {
  HideSlowBannerAction(@NotNull FlutterView view) {
    super(view, "Hide Slow Mode Banner");
  }

  @Override
  protected void perform(AnActionEvent event) {
    assert (view.getFlutterApp() != null);
    view.getFlutterApp().callBooleanExtension("ext.flutter.debugAllowBanner", !isSelected(event));
  }
}

class ShowPaintBaselinesAction extends AbstractToggleableAction {
  ShowPaintBaselinesAction(@NotNull FlutterView view) {
    super(view, "Show Paint Baselines");
  }

  @Override
  protected void perform(AnActionEvent event) {
    assert (view.getFlutterApp() != null);
    view.getFlutterApp().callBooleanExtension("ext.flutter.debugPaintBaselinesEnabled", isSelected(event));
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

    group.add(new PerformanceOverlayAction(view));
    group.add(new ShowPaintBaselinesAction(view));
    group.addSeparator();
    group.add(new RepaintRainbowAction(view));
    group.add(new TimeDilationAction(view));
    group.addSeparator();
    group.add(new HideSlowBannerAction(view));

    return group;
  }
}
