/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.test;


import com.intellij.execution.Location;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.lang.dart.ide.runner.util.DartTestLocationProviderZ;
import com.jetbrains.lang.dart.psi.DartCallExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static io.flutter.run.test.TestConfigUtils.WIDGET_TEST_FUNCTION;

public class FlutterTestLocationProvider extends DartTestLocationProviderZ {
  public static final FlutterTestLocationProvider INSTANCE = new FlutterTestLocationProvider();


  @Nullable
  @Override
  protected Location<PsiElement> getLocationByLineAndColumn(@NotNull PsiFile file, int line, int column) {
    try {
      return super.getLocationByLineAndColumn(file, line, column);
    } catch (IndexOutOfBoundsException e) {
      // Line and column info can be wrong, in which case we fall-back on test and group name for test discovery.
    }

    return null;
  }

  @Override
  protected boolean isTest(@NotNull DartCallExpression expression) {
    return super.isTest(expression) || Objects.equals(WIDGET_TEST_FUNCTION, expression.getExpression().getText());
  }
}
