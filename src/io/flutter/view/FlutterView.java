/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.ide.CommonActionsManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

// TODO: toolbar actions - the 4 flutter options
// TODO: toolbar actions - open in observatory (profile, timeline)

// TODO: display an fps graph
// TODO: toolbar setting for displaying an fps graph
// TODO: display the effective fps
// TODO: display the frame count

// TODO: device connected to
// TODO: open on debug
// TODO: pref setting for opening when starting a debug session

// TODO: if multiple simultaneous running apps, use tabs to keep them separate

// TODO: open on connetion established
// TODO: hook up a toggle debug drawing button

@com.intellij.openapi.components.State(
  name = "FlutterView",
  storages = {@Storage("$WORKSPACE_FILE$")}
)
public class FlutterView implements PersistentStateComponent<FlutterView.State>, Disposable {
  private FlutterView.State state = new FlutterView.State();
  @SuppressWarnings("FieldCanBeLocal") private final Project myProject;
  private ContentManager myContentManager;

  public FlutterView(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void dispose() {
  }

  @Nullable
  @Override
  public FlutterView.State getState() {
    if (this.myContentManager != null) {
      final Content content = this.myContentManager.getSelectedContent();
      this.state.selectedIndex = this.myContentManager.getIndexOfContent(content);
    }

    return this.state;
  }

  @Override
  public void loadState(FlutterView.State state) {
    this.state = state;
  }

  public void initToolWindow(ToolWindow toolWindow) {
    final ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();

    final Content widgetsContent = contentFactory.createContent(null, null, false);
    final WidgetsPanel widgetsPanel = new WidgetsPanel();

    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(CommonActionsManager.getInstance().createCollapseAllAction(null, widgetsPanel));
    group.add(CommonActionsManager.getInstance().createCollapseAllAction(null, widgetsPanel));
    group.addSeparator();
    group.add(CommonActionsManager.getInstance().createCollapseAllAction(null, widgetsPanel));
    widgetsPanel.setToolbar(ActionManager.getInstance().createActionToolbar("FlutterViewToolbar", group, true).getComponent());
    final JPanel panel = new JPanel(new BorderLayout());
    final JPanel titlePanel = new JPanel(new BorderLayout());
    panel.add(titlePanel, BorderLayout.NORTH);
    titlePanel.add(new JLabel("#512"), BorderLayout.WEST);
    //titlePanel.add(new JLabel("50 fps"), BorderLayout.EAST);
    //panel.setBorder(IdeBorderFactory.createTitledBorder("Frames", true));
    widgetsPanel.setContent(panel);
    widgetsContent.setComponent(widgetsPanel);
    Disposer.register(this, widgetsPanel);

    widgetsContent.setCloseable(false);

    this.myContentManager = toolWindow.getContentManager();
    this.myContentManager.addContent(widgetsContent);
    final Content selContent = this.myContentManager.getContent(this.state.selectedIndex);
    this.myContentManager.setSelectedContent(selContent == null ? widgetsContent : selContent);
  }

  // see TodoView.State
  class State {
    @Attribute("selected-index")
    public int selectedIndex;

    State() {
    }
  }

  class WidgetsPanel extends SimpleToolWindowPanel implements Disposable {
    public WidgetsPanel() {
      super(true, true);
    }

    @Override
    public void dispose() {
    }
  }
}
