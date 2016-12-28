/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;

/**
 * A "retargeting" action that redirects to another action that is setup elsewhere with
 * context required to execute.
 */
public abstract class FlutterRetargetAction extends DumbAwareAction {
  public static final String RELOAD_DISPLAY_ID = "Flutter Commands"; //NON-NLS

  @NotNull
  private final String myActionId;

  FlutterRetargetAction(@NotNull String actionId) {
    myActionId = actionId;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final AnAction action = getAction();
    if (action != null) {
      action.actionPerformed(e);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();

    final Project project = e.getProject();
    if (project == null || !FlutterSdkUtil.hasFlutterModule(project)) {
      presentation.setVisible(false);
      return;
    }

    presentation.setVisible(true);

    // Retargeted actions defer to their targets for presentation updates.
    final AnAction action = getAction();
    if (action != null) {
      action.update(e);
    }
    else {
      presentation.setEnabled(false);
    }
  }

  private AnAction getAction() {
    return ActionManager.getInstance().getAction(myActionId);
  }
}
