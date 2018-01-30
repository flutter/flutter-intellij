/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view.preview;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import io.flutter.settings.FlutterSettings;
import io.flutter.view.FlutterViewCondition;

public class PreviewViewCondition implements Condition<Project> {
  @Override
  public boolean value(Project project) {
    return new FlutterViewCondition().value(project) && FlutterSettings.getInstance().isPreviewView();
  }
}
