/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import io.flutter.project.FlutterProjectCreator;
import io.flutter.project.FlutterProjectModel;
import org.jetbrains.annotations.NotNull;

public class FlutterModuleModel extends FlutterProjectModel {
  public FlutterModuleModel(@NotNull FlutterProjectType type) {
    super(type);
  }

  @Override
  protected void handleFinished() {
    // Do not call the superclass method.
    if (projectType().get().isPresent() && projectType().get().get() == FlutterProjectType.IMPORT) {
      String location = projectLocation().get();
      assert (!location.isEmpty());
      new FlutterModuleImporter(this).importModule();
    }
    else {
      assert (!projectName().get().isEmpty());
      assert (!flutterSdk().get().isEmpty());
      new FlutterProjectCreator(this).createModule();
    }
  }
}
