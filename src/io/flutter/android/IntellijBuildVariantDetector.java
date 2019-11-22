/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.android;

import com.intellij.openapi.project.Project;

public class IntellijBuildVariantDetector implements BuildVariantDetector {

  @Override
  public String selectedBuildVariant(Project project) {
    return null;
  }
}
