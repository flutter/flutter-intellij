/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.Disposable;
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

// TODO: toolbar actions
// TODO: reload status
// TODO: connection status
// TODO: fps, fps events
// TODO: tabs
// TODO: open on debug
// TODO: widget tree

@com.intellij.openapi.components.State(
  name = "FlutterView",
  storages = {@Storage("$WORKSPACE_FILE$")}
)
public class FlutterView implements PersistentStateComponent<FlutterView.State>, Disposable {
  private FlutterView.State state = new FlutterView.State();
  private final Project myProject;
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

    final Content widgetsContent = contentFactory.createContent(null, "Widgets", false);
    final WidgetsPanel widgetsPanel = new WidgetsPanel();
    widgetsContent.setComponent(widgetsPanel);
    Disposer.register(this, widgetsPanel);

    final Content elementsContent = contentFactory.createContent(null, "Elements", false);
    final ElementsPanel elementsPanel = new ElementsPanel();
    elementsContent.setComponent(elementsPanel);
    Disposer.register(this, elementsPanel);

    this.myContentManager = toolWindow.getContentManager();
    this.myContentManager.addContent(widgetsContent);
    this.myContentManager.addContent(elementsContent);

    widgetsContent.setCloseable(false);
    elementsContent.setCloseable(false);

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

  class ElementsPanel extends SimpleToolWindowPanel implements Disposable {
    public ElementsPanel() {
      super(true, true);
    }

    @Override
    public void dispose() {
    }
  }
}
