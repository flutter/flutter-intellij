/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.test;


import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.lang.dart.psi.DartCallExpression;
import com.jetbrains.lang.dart.psi.DartFile;
import com.jetbrains.lang.dart.psi.DartStringLiteralExpression;
import io.flutter.FlutterUtils;
import io.flutter.dart.DartSyntax;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

public class TestConfigUtils {

  /**
   * Widget test function as defined in package:flutter_test/src/widget_tester.dart.
   */
  public static final String WIDGET_TEST_FUNCTION = "testWidgets";

  @Nullable
  public static TestType asTestCall(@NotNull PsiElement element) {
    final DartFile file = FlutterUtils.getDartFile(element);
    if (file != null && FlutterUtils.isInTestDir(file)) {
      // Named tests.
      final TestType namedTestCall = findNamedTestCall(element);
      if (namedTestCall != null) return namedTestCall;

      // Main.
      if (isMainFunctionDeclarationWithTests(element)) return TestType.MAIN;
    }

    return null;
  }

  @VisibleForTesting
  public static boolean isMainFunctionDeclarationWithTests(@NotNull PsiElement element) {
    if (DartSyntax.isMainFunctionDeclaration(element)) {
      final PsiElementProcessor.FindElement<PsiElement> processor =
        new PsiElementProcessor.FindElement<PsiElement>() {
          @Override
          public boolean execute(@NotNull PsiElement element) {
            final TestType type = findNamedTestCall(element);
            return type == null || setFound(element);
          }
        };

      PsiTreeUtil.processElements(element, processor);
      return processor.isFound();
    }

    return false;
  }

  @Nullable
  private static TestType findNamedTestCall(@NotNull PsiElement element) {
    if (element instanceof DartCallExpression) {
      final DartCallExpression call = (DartCallExpression)element;
      for (TestType type : TestType.values()) {
        if (type.matchesFunction(call)) return type;
      }
    }
    return null;
  }

  /**
   * Returns the name of the test containing this element, or null if it can't be calculated.
   */
  @Nullable
  public static String findTestName(@Nullable PsiElement elt) {
    if (elt == null) return null;

    final DartCallExpression call = TestType.findTestCall(elt);
    if (call == null) return null;

    final DartStringLiteralExpression lit = DartSyntax.getArgument(call, 0, DartStringLiteralExpression.class);
    if (lit == null) return null;

    return DartSyntax.unquote(lit);
  }

  enum TestType {
    GROUP(AllIcons.RunConfigurations.TestState.Run_run, "group"),
    MAIN(AllIcons.RunConfigurations.TestState.Run_run) {
      @NotNull
      String getTooltip(@NotNull PsiElement element) {
        return "Run Tests";
      }
    },
    SINGLE(AllIcons.RunConfigurations.TestState.Run, "test", WIDGET_TEST_FUNCTION);

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
    Icon getIcon() {
      return myIcon;
    }

    boolean matchesFunction(@NotNull DartCallExpression element) {
      return myTestFunctionNames.stream().anyMatch(name -> DartSyntax.isCallToFunctionNamed(element, name));
    }

    @NotNull
    String getTooltip(@NotNull PsiElement element) {
      final String testName = findTestName(element);
      if (testName != null && !testName.isEmpty()) {
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
}
