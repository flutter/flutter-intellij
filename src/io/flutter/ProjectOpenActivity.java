/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;

/**
 * Runs startup actions just after a project is opened, before it's indexed.
 *
 * @see FlutterInitializer for actions that run later.
 */
public class ProjectOpenActivity implements StartupActivity, DumbAware {
  @Override
  public void runActivity(@NotNull Project project) {
    final FlutterSdk sdk = FlutterSdk.getIncomplete(project);
    if (sdk != null && sdk.getDartSdkPath() == null) {
      final boolean ok = sdk.sync(project);
      if (!ok) {
        LOG.warn("failed to sync flutter SDK");
      }
    }
  }

  private static final Logger LOG = Logger.getInstance(ProjectOpenActivity.class.getName());
}
