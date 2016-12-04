/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import io.flutter.run.daemon.FlutterDaemonService;
import org.jetbrains.annotations.NotNull;

public class FlutterInitializer implements StartupActivity {

  @Override
  public void runActivity(@NotNull Project project) {
    FlutterDaemonService.getInstance(project);
  }
}
