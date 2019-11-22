/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.android;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

/// Extension point to determine the selected build variant for the :flutter module of a <code>project</code>.
public interface BuildVariantDetector {
  ExtensionPointName<BuildVariantDetector> EP_NAME = ExtensionPointName.create("io.flutter.buildVariantDetector");

  String selectedBuildVariant(Project project);
}