/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.common;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.lang.dart.psi.DartCallExpression;
import com.jetbrains.lang.dart.psi.DartStringLiteralExpression;
import io.flutter.dart.DartSyntax;
import org.apache.commons.lang.StringEscapeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Common utilities for processing Flutter tests.
 * <p>
 * This class is useful for identifying the {@link TestType} of different Dart objects
 */
public abstract class CommonTestConfigUtils {
  /**
   * Regex that matches customized versions of the Widget test function from package:flutter_test/src/widget_tester.dart.
   * <p>
   * This will match all test methods with names that start with 'test', optionally
   * have additional text in the middle, and end with 'Widgets'.
   */
  public static final Pattern WIDGET_TEST_REGEX = Pattern.compile("test([A-Z][A-Za-z0-9_$]*)?Widgets");

  public abstract TestType asTestCall(@NotNull PsiElement element);

  @VisibleForTesting
  public boolean isMainFunctionDeclarationWithTests(@NotNull PsiElement element) {
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
  protected TestType findNamedTestCall(@NotNull PsiElement element) {
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
  public String findTestName(@Nullable PsiElement elt) {
    if (elt == null) return null;

    final DartCallExpression call = TestType.findTestCall(elt);
    if (call == null) return null;

    final DartStringLiteralExpression lit = DartSyntax.getArgument(call, 0, DartStringLiteralExpression.class);
    if (lit == null) return null;

    final String name = DartSyntax.unquote(lit);
    if (name == null) return null;

    return StringEscapeUtils.unescapeJava(name);
  }
}
