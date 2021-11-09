/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.editor;

import com.intellij.codeHighlighting.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Registrar that adds the WidgetIndentsHighlightingPassFactory.
 *
 * It is now neccessary to register highlighting passes with a registrar to
 * ensure that highlighting passes do not unpredictably disappear.
 */
public class WidgetIndentsHighlightingPassFactoryRegistrar implements TextEditorHighlightingPassFactoryRegistrar, DumbAware {

  @Override
  public void registerHighlightingPassFactory(@NotNull TextEditorHighlightingPassRegistrar registrar, @NotNull Project project) {
    if (FlutterModuleUtils.hasFlutterModule(project)) {
      WidgetIndentsHighlightingPassFactory.getInstance(project).registerHighlightingPassFactory(registrar);
    }
  }
}

