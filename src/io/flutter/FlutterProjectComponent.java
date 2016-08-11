/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import org.jetbrains.annotations.NotNull;

// TODO Should this extend DartProjectComponent?
public class FlutterProjectComponent extends AbstractProjectComponent {
  protected FlutterProjectComponent(Project project) {
    super(project);
  }

  @NotNull
  public static ModificationTracker getProjectRootsModificationTracker(@NotNull final Project project) {
    // TODO Implement getProjectRootsModificationTracker()
    return ModificationTracker.NEVER_CHANGED;
  }
}
