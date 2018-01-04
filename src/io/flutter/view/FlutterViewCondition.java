/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import io.flutter.utils.FlutterModuleUtils;

public class FlutterViewCondition implements Condition<Project> {
  @Override
  public boolean value(Project project) {
    return project != null && (FlutterModuleUtils.hasFlutterModule(project) || FlutterModuleUtils.isFlutterBazelProject(project));
  }
}
