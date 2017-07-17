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
    if (action != null) {
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
    presentation.setEnabled(false);

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
  }

  private AnAction getAction() {
    return ActionManager.getInstance().getAction(myActionId);
  }
}
