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
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Different scopes of test that can be run on a Flutter app.
 *
 * <p>
 * Each scope corresponds to a 'test', 'group', or 'main' method declaration in a Dart test file.
 */
public enum TestType {
  // Note that mapping elements to their most specific enclosing function call depends on the ordering from most to least specific.
  SINGLE(AllIcons.RunConfigurations.TestState.Run, CommonTestConfigUtils.WIDGET_TEST_REGEX, "test"),
  GROUP(AllIcons.RunConfigurations.TestState.Run_run, "group"),
  /**
   * This {@link TestType} doesn't know how to detect main methods.
   * The logic to detect main methods is in {@link CommonTestConfigUtils}.
   */
  MAIN(AllIcons.RunConfigurations.TestState.Run_run) {
    @NotNull
    public String getTooltip(@NotNull PsiElement element) {
      return "Run Tests";
    }
  };

  @NotNull
  private final Icon myIcon;
  private final List<String> myTestFunctionNames;
  private final Pattern myTestFunctionRegex;

  TestType(@NotNull Icon icon, String... testFunctionNames) {
    this(icon, null, testFunctionNames);
  }

  TestType(@NotNull Icon icon, @Nullable Pattern testFunctionRegex, String... testFunctionNames) {
    myIcon = icon;
    myTestFunctionRegex = testFunctionRegex;
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

  /**
   * Describes whether the given {@param element} matches one of the names this {@link TestType} is set up to look for.
   * <p>
   * Does not match the main function.
   */
  boolean matchesFunction(@NotNull DartCallExpression element) {
    final boolean hasTestFunctionName = myTestFunctionNames.stream().anyMatch(name -> DartSyntax.isCallToFunctionNamed(element, name));
    if (!hasTestFunctionName && myTestFunctionRegex != null) {
      return DartSyntax.isCallToFunctionMatching(element, myTestFunctionRegex);
    }
    return hasTestFunctionName;
  }

  /**
   * Describes the tooltip to show on a particular {@param element}.
   */
  @NotNull
  public String getTooltip(@NotNull PsiElement element, @NotNull CommonTestConfigUtils testConfigUtils) {
    final String testName = testConfigUtils.findTestName(element);
    if (StringUtils.isNotEmpty(testName)) {
      return "Run '" + testName + "'";
    }

    return "Run Test";
  }

  /**
   * Finds the closest corresponding test function of this {@link TestType} that encloses the given {@param element}.
   */
  @Nullable
  public DartCallExpression findCorrespondingCall(@NotNull PsiElement element) {
    for (String name : myTestFunctionNames) {
      final DartCallExpression call = DartSyntax.findEnclosingFunctionCall(element, name);
      if (call != null) {
        return call;
      }
    }
    if (myTestFunctionRegex != null) {
      return DartSyntax.findEnclosingFunctionCall(element, myTestFunctionRegex);
    }
    return null;
  }
}
