/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

public abstract class FlutterKeyAction extends DumbAwareAction {
  public static final String RELOAD_DISPLAY_ID = "Flutter Commands"; //NON-NLS

  @NotNull
  private final String myActionId;

  FlutterKeyAction(@NotNull String actionId) {
    myActionId = actionId;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final AnAction action = ActionManager.getInstance().getAction(myActionId);
    if (action != null) {
      action.actionPerformed(e);
    }
  }
}
