/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.FlutterInitializer;
import io.flutter.actions.OpenObservatoryAction;
import io.flutter.run.daemon.FlutterApp;
import org.dartlang.vm.service.VmServiceListener;
import org.dartlang.vm.service.element.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

// TODO(devoncarew): Display an fps graph.
// TODO(devoncarew): Have a pref setting for opening when starting a debug session.

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

    final Content toolContent = contentFactory.createContent(null, null, false);
    final SimpleToolWindowPanel toolWindowPanel = new SimpleToolWindowPanel(true, true);
    toolContent.setComponent(toolWindowPanel);
    //Disposer.register(this, toolWindowPanel);
    toolContent.setCloseable(false);

    final DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.add(new DebugDrawAction(this));
    toolbarGroup.add(new RepaintRainbowAction(this));
    toolbarGroup.add(new PerformanceOverlayAction(this));
    toolbarGroup.add(new TimeDilationAction(this));
    toolbarGroup.addSeparator();
    toolbarGroup.add(new ObservatoryTimelineAction(this));

    final JPanel mainContent = new JPanel(new BorderLayout());

    toolWindowPanel.setToolbar(ActionManager.getInstance().createActionToolbar("FlutterViewToolbar", toolbarGroup, true).getComponent());
    toolWindowPanel.setContent(mainContent);

    final ContentManager contentManager = toolWindow.getContentManager();
    contentManager.addContent(toolContent);
    contentManager.setSelectedContent(toolContent);
  }

  /**
   * Called when a debug connection starts.
   */
  public void debugActive(@NotNull FlutterViewMessages.FlutterDebugEvent event) {
    this.app = event.app;

    event.vmService.addVmServiceListener(new VmServiceListener() {
      @Override
      public void connectionOpened() {
      }

      @Override
      public void received(String s, Event event) {
      }

      @Override
      public void connectionClosed() {
        FlutterView.this.app = null;
        updateIcon();
      }
    });

    updateIcon();
  }

  FlutterApp getFlutterApp() {
    return app;
  }

  private void updateIcon() {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myProject.isDisposed()) {
        return;
      }

      final ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(TOOL_WINDOW_ID);
      if (toolWindow == null) return;

      if (app == null) {
        toolWindow.setIcon(FlutterIcons.Flutter);
      } else {
        toolWindow.setIcon(ExecutionUtil.getLiveIndicator(FlutterIcons.Flutter));
      }
    });
  }

  /**
   * State for the view.
   */
  class State {
  }
}

abstract class AbstractToggleableAction extends DumbAwareAction implements Toggleable {
  @NotNull final FlutterView view;
  private boolean selected = false;

  AbstractToggleableAction(@NotNull FlutterView view, @Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);

    this.view = view;
  }

  @Override
  public final void update(AnActionEvent e) {
    // enabled
    e.getPresentation().setEnabled(view.getFlutterApp() != null);

    // selected
    final boolean selected = this.isSelected(e);
    final Presentation presentation = e.getPresentation();
    presentation.putClientProperty("selected", selected);
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    this.setSelected(event, !isSelected(event));
    final Presentation presentation = event.getPresentation();
    presentation.putClientProperty("selected", isSelected(event));

    FlutterInitializer.sendAnalyticsAction(this);
    perform(event);
  }

  protected abstract void perform(AnActionEvent event);

  public boolean isSelected(AnActionEvent var1) {
    return selected;
  }

  public void setSelected(AnActionEvent event, boolean selected) {
    this.selected = selected;

    ApplicationManager.getApplication().invokeLater(() -> this.update(event));
  }
}

class DebugDrawAction extends AbstractToggleableAction {
  DebugDrawAction(@NotNull FlutterView view) {
    super(view, FlutterBundle.message("flutter.view.debugPaint.text"), FlutterBundle.message("flutter.view.debugPaint.description"),
          AllIcons.General.TbShown);
  }

  protected void perform(AnActionEvent event) {
    final Map<String, Object> params = new HashMap<>();
    params.put("enabled", isSelected(event));
    view.getFlutterApp().callServiceExtension("ext.flutter.debugPaint", params);
  }
}

class RepaintRainbowAction extends AbstractToggleableAction {
  RepaintRainbowAction(@NotNull FlutterView view) {
    super(view, FlutterBundle.message("flutter.view.repaintRainbow.text"), FlutterBundle.message("flutter.view.repaintRainbow.description"),
          AllIcons.Gutter.Colors);
  }

  protected void perform(AnActionEvent event) {
    final Map<String, Object> params = new HashMap<>();
    params.put("enabled", isSelected(event));
    view.getFlutterApp().callServiceExtension("ext.flutter.repaintRainbow", params);
  }
}

class PerformanceOverlayAction extends AbstractToggleableAction {
  PerformanceOverlayAction(@NotNull FlutterView view) {
    super(view, FlutterBundle.message("flutter.view.performanceOverlay.text"),
          FlutterBundle.message("flutter.view.performanceOverlay.description"), AllIcons.General.LocateHover);
  }

  protected void perform(AnActionEvent event) {
    final Map<String, Object> params = new HashMap<>();
    params.put("enabled", isSelected(event));
    view.getFlutterApp().callServiceExtension("ext.flutter.showPerformanceOverlay", params);
  }
}

class TimeDilationAction extends AbstractToggleableAction {
  TimeDilationAction(@NotNull FlutterView view) {
    super(view, FlutterBundle.message("flutter.view.slowAnimations.text"), FlutterBundle.message("flutter.view.slowAnimations.description"),
          AllIcons.General.MessageHistory);
  }

  protected void perform(AnActionEvent event) {
    final Map<String, Object> params = new HashMap<>();
    params.put("timeDilation", isSelected(event) ? 5.0 : 1.0);
    view.getFlutterApp().callServiceExtension("ext.flutter.timeDilation", params);
  }
}

abstract class AbstractObservatoryAction extends DumbAwareAction {
  @NotNull final FlutterView view;

  AbstractObservatoryAction(@NotNull FlutterView view, @Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);

    this.view = view;
  }

  @Override
  public final void update(AnActionEvent e) {
    e.getPresentation().setEnabled(view.getFlutterApp() != null);
  }
}

class ObservatoryTimelineAction extends AbstractObservatoryAction {
  ObservatoryTimelineAction(@NotNull FlutterView view) {
    super(view, FlutterBundle.message("flutter.view.observatoryTimeline.text"),
          FlutterBundle.message("flutter.view.observatoryTimeline.description"),
          AllIcons.Debugger.Value);
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    FlutterInitializer.sendAnalyticsAction(this);

    final String httpUrl = view.getFlutterApp().getConnector().getBrowserUrl();
    if (httpUrl != null) {
      OpenObservatoryAction.openInAnyChromeFamilyBrowser(httpUrl + "/#/timeline");
    }
  }
}
