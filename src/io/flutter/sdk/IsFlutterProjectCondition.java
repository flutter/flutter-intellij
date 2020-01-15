/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import io.flutter.utils.FlutterModuleUtils;

import java.util.List;

/**
 * A condition that returns true if any modules include a content root with a Dart package
 * with a 'flutter:' dependency.
 */
public class IsFlutterProjectCondition implements Condition<Project> {
  @Override
  public boolean value(Project project) {
    final List<Module> flutterModules = FlutterModuleUtils.findModulesWithFlutterContents(project);

    return !flutterModules.isEmpty();
  }
}
