/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.common;

import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiElement;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Different scopes of test that can be run on a Flutter app.
 *
 * <p>
 * Each scope corresponds to a 'test', 'group', or 'main' method declaration in a Dart test file.
 */
public enum TestType {
  // Note that mapping elements to their most specific enclosing function call depends on the ordering from most to least specific.
  SINGLE(AllIcons.RunConfigurations.TestState.Run),
  GROUP(AllIcons.RunConfigurations.TestState.Run_run),
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

  TestType(@NotNull Icon icon) {
    myIcon = icon;
  }

  @NotNull
  public Icon getIcon() {
    return myIcon;
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
}
