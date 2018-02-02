/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import io.flutter.settings.FlutterSettings;

public class PreviewViewCondition implements Condition<Project> {
  @Override
  public boolean value(Project project) {
    return FlutterSettings.getInstance().getEnablePreviewView();
  }
}
