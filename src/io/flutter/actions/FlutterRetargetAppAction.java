/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import io.flutter.run.FlutterDebugProcess;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A "retargeting" action that redirects to another action that is setup elsewhere with
 * context required to execute.
 */
public abstract class FlutterRetargetAppAction extends DumbAwareAction {
  public static final String RELOAD_DISPLAY_ID = "Flutter Commands"; //NON-NLS

  public interface AppAction {
    void actionPerformed(FlutterApp app);
  }

  @NotNull
  private final String myActionId;

  @NotNull
  private final List<String> myPlaces = new ArrayList<>();

  FlutterRetargetAppAction(@NotNull String actionId,
                           @Nullable String text,
                           @Nullable String description,
                           @SuppressWarnings("SameParameterValue") @NotNull String... places) {
    super(text, description, null);
    myActionId = actionId;
    myPlaces.addAll(Arrays.asList(places));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final AnAction action = getAction();

    if (action instanceof AppAction) {
      final FlutterApp app = getCurrentApp(e.getProject());
      if (app != null) {
        ((AppAction)action).actionPerformed(app);
      }
    }
    else if (action != null) {
      action.actionPerformed(e);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();

    final Project project = e.getProject();
    if (project == null || !FlutterModuleUtils.hasFlutterModule(project) || !myPlaces.contains(e.getPlace())) {
      presentation.setVisible(false);
      return;
    }

    presentation.setVisible(true);

    // Retargeted actions defer to their targets for presentation updates.
    final AnAction action = getAction();
    if (action != null) {
      final Presentation template = action.getTemplatePresentation();
      final String text = template.getTextWithMnemonic();
      if (text != null) {
        presentation.setText(text, true);
      }
      action.update(e);
    }
    else {
      presentation.setEnabled(false);
    }
  }

  private FlutterApp getCurrentApp(Project project) {
    final XDebuggerManager manager = XDebuggerManager.getInstance(project);
    final XDebugSession session = manager.getCurrentSession();
    if (session == null) {
      return null;
    }
    final XDebugProcess process = session.getDebugProcess();
    if (process instanceof FlutterDebugProcess) {
      final FlutterDebugProcess flutterProcess = (FlutterDebugProcess)process;
      return flutterProcess.getConnector().getApp();
    }
    return null;
  }

  private AnAction getAction() {
    return ActionManager.getInstance().getAction(myActionId);
  }
}
