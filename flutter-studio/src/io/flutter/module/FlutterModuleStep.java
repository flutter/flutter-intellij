/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import io.flutter.project.FlutterProjectModel;
import io.flutter.project.FlutterProjectStep;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class FlutterModuleStep extends FlutterProjectStep {
  public FlutterModuleStep(FlutterProjectModel model, String title, Icon icon, FlutterProjectType type) {
    super(model, title, icon, type);
    hideLocation();
  }

  @NotNull
  @Override
  public String getContainerName() {
    return "module";
  }
}
