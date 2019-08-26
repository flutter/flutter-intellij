/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.android;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class AndroidStudioGradleSyncProvider implements GradleSyncProvider {

  @Override
  public void scheduleSync(@NotNull Project project) {
    GradleSyncInvoker.getInstance().requestProjectSync(
      project,
      new GradleSyncInvoker.Request(TRIGGER_PROJECT_MODIFIED));
  }
}
