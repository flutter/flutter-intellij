/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.common;

import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiElement;
import com.jetbrains.lang.dart.psi.DartCallExpression;
import io.flutter.dart.DartSyntax;
import io.flutter.run.test.TestConfigUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

/**
 * Different scopes of test that can be run on a Flutter app.
 *
 * <p>
 * Each scope corresponds to a 'test', 'group', or 'main' method declaration in a Dart test file.
 */
public enum TestType {
  // Note that mapping elements to their most specific enclosing function call depends on the ordering from most to least specific.
  SINGLE(AllIcons.RunConfigurations.TestState.Run, "test", CommonTestConfigUtils.WIDGET_TEST_FUNCTION),
  GROUP(AllIcons.RunConfigurations.TestState.Run_run, "group"),
  MAIN(AllIcons.RunConfigurations.TestState.Run_run) {
    @NotNull
    public String getTooltip(@NotNull PsiElement element) {
      return "Run Tests";
    }
  };

  @NotNull
  private final Icon myIcon;
  private final List<String> myTestFunctionNames;

  TestType(@NotNull Icon icon, String... testFunctionNames) {
    myIcon = icon;
    myTestFunctionNames = Arrays.asList(testFunctionNames);
  }

  @Nullable
  public static DartCallExpression findTestCall(@NotNull PsiElement element) {
    for (TestType type : TestType.values()) {
      final DartCallExpression call = type.findCorrespondingCall(element);
      if (call != null) return call;
    }
    return null;
  }

  @NotNull
  public Icon getIcon() {
    return myIcon;
  }

  boolean matchesFunction(@NotNull DartCallExpression element) {
    return myTestFunctionNames.stream().anyMatch(name -> DartSyntax.isCallToFunctionNamed(element, name));
  }

  @NotNull
  public String getTooltip(@NotNull PsiElement element, @NotNull CommonTestConfigUtils testConfigUtils) {
    final String testName = testConfigUtils.findTestName(element);
    if (StringUtils.isNotEmpty(testName)) {
      return "Run '" + testName + "'";
    }

    return "Run Test";
  }

  @Nullable
  public DartCallExpression findCorrespondingCall(@NotNull PsiElement element) {
    for (String name : myTestFunctionNames) {
      final DartCallExpression call = DartSyntax.findEnclosingFunctionCall(element, name);
      if (call != null) {
        return call;
      }
    }
    return null;
  }
}
