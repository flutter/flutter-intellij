/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class FlutterModuleType extends ModuleType<FlutterModuleBuilder> {
  private static final String ID = "FLUTTER_MODULE_TYPE";

  public FlutterModuleType() {
    super(ID);
  }

  public static FlutterModuleType getInstance() {
    return (FlutterModuleType)ModuleTypeManager.getInstance().findByID(ID);
  }

  @NotNull
  @Override
  public FlutterModuleBuilder createModuleBuilder() {
    return new FlutterModuleBuilder();
  }

  @NotNull
  @Override
  public String getName() {
    return "Flutter";
  }

  @NotNull
  @Override
  public String getDescription() {
    return FlutterBundle.message("flutter.project.description");
  }

  // This method does not exist in 2017.2.
  @SuppressWarnings("override")
  public Icon getBigIcon() {
    return FlutterIcons.Flutter_2x;
  }

  @Override
  public Icon getNodeIcon(@Deprecated boolean b) {
    return FlutterIcons.Flutter;
  }
}
