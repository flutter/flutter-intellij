/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import io.flutter.project.FlutterProjectModel;
import javax.swing.Icon;

// TODO(messick) Is this needed?
public class FlutterAddToAppModuleStep extends FlutterModuleStep {
  public FlutterAddToAppModuleStep(FlutterProjectModel model, String title, Icon icon, FlutterProjectType type) {
    super(model, title, icon, type);
  }
}
