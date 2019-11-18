/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import io.flutter.project.FlutterProjectModel;
import io.flutter.project.FlutterProjectStep;
import io.flutter.utils.AndroidUtils;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

public class FlutterModuleStep extends FlutterProjectStep {
  public FlutterModuleStep(FlutterProjectModel model, String title, Icon icon, FlutterProjectType type) {
    super(model, title, icon, type);
    if (!AndroidUtils.isAndroidProject(model.project().getValue())) {
      hideLocation();
    }
  }

  @NotNull
  @Override
  public String getContainerName() {
    return "module";
  }

  @Override
  protected boolean isProject() {
    return false;
  }
}
