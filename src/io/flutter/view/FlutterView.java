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
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.xmlb.annotations.Attribute;
import com.jetbrains.lang.dart.ide.runner.server.vmService.VmServiceConsumers;
import com.jetbrains.lang.dart.ide.runner.server.vmService.VmServiceWrapper;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.element.VM;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

// TODO(devoncarew): toolbar actions - the 4 flutter options
// TODO(devoncarew): toolbar actions - open in observatory (profile, timeline)

// TODO(devoncarew): display an fps graph

// TODO(devoncarew): device connected to
// TODO(devoncarew): pref setting for opening when starting a debug session

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

    final Content toolContent = contentFactory.createContent(null, null, false);
    final SimpleToolWindowPanel toolWindowPanel = new SimpleToolWindowPanel(true, true);
    toolContent.setComponent(toolWindowPanel);
    //Disposer.register(this, toolWindowPanel);
    toolContent.setCloseable(false);

    final DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.add(CommonActionsManager.getInstance().createCollapseAllAction(null, toolWindowPanel));
    toolbarGroup.add(CommonActionsManager.getInstance().createCollapseAllAction(null, toolWindowPanel));
    toolbarGroup.addSeparator();
    toolbarGroup.add(CommonActionsManager.getInstance().createCollapseAllAction(null, toolWindowPanel));

    final JPanel mainContent = new JPanel(new BorderLayout());

    toolWindowPanel.setToolbar(ActionManager.getInstance().createActionToolbar("FlutterViewToolbar", toolbarGroup, true).getComponent());
    toolWindowPanel.setContent(mainContent);

    this.myContentManager = toolWindow.getContentManager();
    this.myContentManager.addContent(toolContent);
    final Content selContent = this.myContentManager.getContent(this.state.selectedIndex);
    this.myContentManager.setSelectedContent(selContent == null ? toolContent : selContent);
  }

  /**
   * Called when a debug connection starts.
   */
  public void debugActive(VmServiceWrapper wrapper, VmService vmService) {
    // TODO: we need some reflection utils

    System.out.println("connection active; system ready");

    vmService.getVM(new VmServiceConsumers.VmConsumerWrapper() {
      @Override
      public void received(VM vm) {
        System.out.println(vm.getHostCPU());
        System.out.println(vm.getTargetCPU());
        System.out.println(vm.getVersion());
      }
    });
  }

  // TODO(devoncarew): Remember any state here.
  // see TodoView.State
  class State {
    @Attribute("selected-index")
    public int selectedIndex;

    State() {
    }
  }
}
