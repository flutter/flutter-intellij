/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.test;


import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiElement;
import com.jetbrains.lang.dart.psi.DartCallExpression;
import com.jetbrains.lang.dart.psi.DartFile;
import com.jetbrains.lang.dart.psi.DartStringLiteralExpression;
import io.flutter.FlutterUtils;
import io.flutter.dart.DartSyntax;
import io.flutter.run.FlutterRunConfigurationProducer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class TestConfigUtils {

  enum TestType {
    GROUP(AllIcons.RunConfigurations.TestState.Run_run),
    MAIN(AllIcons.RunConfigurations.TestState.Run_run) {
      @NotNull
      String getTooltip(@NotNull PsiElement element) {
        return "Run Tests";
      }
    },
    SINGLE(AllIcons.RunConfigurations.TestState.Run);

    @NotNull
    private final Icon myIcon;

    TestType(@NotNull Icon icon) {
      myIcon = icon;
    }

    @NotNull
    Icon getIcon() {
      return myIcon;
    }

    @NotNull
    String getTooltip(@NotNull PsiElement element) {
      final String testName = findTestName(element);
      if (testName != null && !testName.isEmpty()) {
        return "Run '" + testName + "'";
      }

      return "Run Test";
    }
  }

  @Nullable
  public static TestType asTestCall(@NotNull PsiElement element) {
    final DartFile file = FlutterRunConfigurationProducer.getDartFile(element);
    if (file != null && FlutterUtils.isInTestDir(file)) {
      if (DartSyntax.isCallToFunctionNamed(element, "test")) return TestType.SINGLE;
      if (DartSyntax.isCallToFunctionNamed(element, "group")) return TestType.GROUP;
      if (DartSyntax.isFunctionDeclarationNamed(element, "main")) return TestType.MAIN;
    }

    return null;
  }


  /**
   * Returns the name of the test containing this element, or null if it can't be calculated.
   */
  @Nullable
  public static String findTestName(@Nullable PsiElement elt) {
    if (elt == null) return null;

    DartCallExpression call = DartSyntax.findEnclosingFunctionCall(elt, "test");
    if (call == null) {
      call = DartSyntax.findEnclosingFunctionCall(elt, "group");
    }
    if (call == null) return null;

    final DartStringLiteralExpression lit = DartSyntax.getArgument(call, 0, DartStringLiteralExpression.class);
    if (lit == null) return null;

    return DartSyntax.unquote(lit);
  }
}
