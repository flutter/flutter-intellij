/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Store and retrieve actions relative to a Project.
 */
public class ProjectActions {
  private static final Key<Map<String, AnAction>> PROJECT_ACTIONS_KEY = Key.create("ProjectActions");

  public static void registerAction(@NotNull Project project, @NotNull String id, @NotNull AnAction action) {
    Map<String, AnAction> actions = project.getUserData(PROJECT_ACTIONS_KEY);
    if (actions == null) {
      actions = new HashMap<>();
      project.putUserData(PROJECT_ACTIONS_KEY, actions);
    }
    actions.put(id, action);
  }

  @Nullable
  public static AnAction getAction(@NotNull Project project, @NotNull String id) {
    final Map<String, AnAction> actions = project.getUserData(PROJECT_ACTIONS_KEY);
    return actions == null ? null : actions.get(id);
  }

  public static void unregisterAction(@NotNull Project project, @NotNull String id) {
    final Map<String, AnAction> actions = project.getUserData(PROJECT_ACTIONS_KEY);
    if (actions != null) {
      actions.remove(id);
    }
  }

  private ProjectActions() {

  }
}
