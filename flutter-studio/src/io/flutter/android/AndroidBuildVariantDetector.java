/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.android;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;

public class AndroidBuildVariantDetector implements BuildVariantDetector {

  @Override
  public String selectedBuildVariant(Project project) {
    Module flutterModule = ModuleManager.getInstance(project).findModuleByName("flutter");
    if (flutterModule == null) {
      return null;
    }
    AndroidFacet facet = AndroidFacet.getInstance(flutterModule);
    if (facet == null) {
      return null;
    }
    String variant = facet.getProperties().SELECTED_BUILD_VARIANT;
    if (variant == null || variant.isEmpty()) {
      return null;
    }
    return variant;
  }
}