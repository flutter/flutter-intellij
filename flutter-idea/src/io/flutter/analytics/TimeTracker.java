/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.analytics;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@Service
public final class TimeTracker {
  private final Project project;
  private Long projectOpenTime;

  @NotNull
  public static TimeTracker getInstance(@NotNull final Project project) {
    return ServiceManager.getService(project, TimeTracker.class);
  }

  public TimeTracker(Project project) {
    this.project = project;
  }

  public void onProjectOpen() {
    this.projectOpenTime = System.currentTimeMillis();
  }

  public int millisSinceProjectOpen() {
    if (projectOpenTime == null) {
      return 0;
    }
    return (int) (System.currentTimeMillis() - projectOpenTime);
  }
}
