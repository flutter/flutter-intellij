/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import io.flutter.FlutterBundle;
import io.flutter.FlutterErrors;
import io.flutter.sdk.FlutterSdk;

public class FlutterDoctorAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(FlutterDoctorAction.class);

  @Override
  public void actionPerformed(AnActionEvent event) {
    final Project project = DumbAwareAction.getEventProject(event);
    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);

    if (sdk != null) {
      try {
        sdk.runProject(project, "Flutter doctor", null, "doctor");
      }
      catch (ExecutionException e) {
        FlutterErrors.showError(
          FlutterBundle.message("flutter.command.exception.title"),
          FlutterBundle.message("flutter.command.exception.message", e.getMessage()));
        LOG.warn(e);
      }
    }
    else {
      FlutterErrors.showError(
        FlutterBundle.message("flutter.sdk.notAvailable.title"),
        FlutterBundle.message("flutter.sdk.notAvailable.message"));
    }
  }
}
