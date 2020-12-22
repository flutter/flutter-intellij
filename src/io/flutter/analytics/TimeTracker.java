/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.analytics;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;

@Service
public final class TimeTracker {
  private final Project project;
  private Long projectOpenTime;

  public TimeTracker(Project project) {
    this.project = project;
  }

  public void setProjectOpenTime() {
    this.projectOpenTime = System.currentTimeMillis();
  }

  public Long millisSinceProjectOpen() {
    if (projectOpenTime == null) {
      return null;
    }
    return System.currentTimeMillis() - projectOpenTime;
  }
}
