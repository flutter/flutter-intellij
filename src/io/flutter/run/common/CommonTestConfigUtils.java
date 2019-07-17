/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.common;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.jetbrains.lang.dart.psi.DartCallExpression;
import com.jetbrains.lang.dart.psi.DartStringLiteralExpression;
import io.flutter.dart.DartSyntax;
import io.flutter.editor.ActiveEditorsOutlineService;
import org.apache.commons.lang.StringEscapeUtils;
import org.dartlang.analysis.server.protocol.FlutterOutline;
import org.dartlang.analysis.server.protocol.Outline;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Common utilities for processing Flutter tests.
 * <p>
 * This class is useful for identifying the {@link TestType} of different Dart objects
 */
public abstract class CommonTestConfigUtils {
  /**
   * How the Dart Analysis Server identifies runnable tests in the {@link Outline} it generates.
   */
  private static final String UNIT_TEST_TEST = "UNIT_TEST_TEST";

  /**
   * How the Dart Analysis Server identifies runnable test groups in the {@link Outline} it generates.
   */
  private static final String UNIT_TEST_GROUP = "UNIT_TEST_GROUP";

  public static String convertHttpServiceProtocolToWs(String url) {
    return StringUtil.trimTrailing(
      url.replaceFirst("http:", "ws:"), '/') + "/ws";
  }

  public abstract TestType asTestCall(@NotNull PsiElement element);

  /**
   * Gets the elements from the outline that are runnable tests.
   */
  protected Map<DartCallExpression, TestType> getTestsFromOutline(@NotNull PsiFile file) {
    final Project project = file.getProject();
    final FlutterOutline outline = getActiveEditorsOutlineService(project).get(file.getVirtualFile());
    final Map<DartCallExpression, TestType> callToTestType = new HashMap<>();
    if (outline != null) {
      visit(outline, callToTestType, file);
    }
    return callToTestType;
  }

  private void visit(@NotNull FlutterOutline outline, @NotNull Map<DartCallExpression, TestType> callToTestType, @NotNull PsiFile file) {
    @NotNull final PsiElement element = Objects.requireNonNull(file.findElementAt(outline.getOffset()));
    if (outline.getDartElement() != null) {
      switch (outline.getDartElement().getKind()) {
        case UNIT_TEST_GROUP:
          // We found a test group.
          callToTestType.put(DartSyntax.findClosestEnclosingFunctionCall(element), TestType.GROUP);
          break;
        case UNIT_TEST_TEST:
          // We found a unit test.
          callToTestType.put(DartSyntax.findClosestEnclosingFunctionCall(element), TestType.SINGLE);
          break;
        default:
          // We found no test.
          break;
      }
    }
    if (outline.getChildren() != null) {
      for (FlutterOutline child : outline.getChildren()) {
        visit(child, callToTestType, file);
      }
    }
  }

  @VisibleForTesting
  public boolean isMainFunctionDeclarationWithTests(@NotNull PsiElement element) {
    if (DartSyntax.isMainFunctionDeclaration(element)) {
      final Map<DartCallExpression, TestType> callToTestType = getTestsFromOutline(element.getContainingFile());
      return !callToTestType.isEmpty();
    }
    return false;
  }

  @Nullable
  protected TestType findNamedTestCall(@NotNull PsiElement element) {
    if (element instanceof DartCallExpression) {
      final DartCallExpression call = (DartCallExpression)element;
      return getTestsFromOutline(element.getContainingFile()).get(call);
    }
    return null;
  }

  /**
   * Returns the name of the test containing this element, or null if it can't be calculated.
   */
  @Nullable
  public String findTestName(@Nullable PsiElement elt) {
    if (elt == null) return null;

    final DartCallExpression call = findEnclosingTestCall(elt, getTestsFromOutline(elt.getContainingFile()));
    if (call == null) return null;

    final DartStringLiteralExpression lit = DartSyntax.getArgument(call, 0, DartStringLiteralExpression.class);
    if (lit == null) return null;

    final String name = DartSyntax.unquote(lit);
    if (name == null) return null;

    return StringEscapeUtils.unescapeJava(name);
  }

  @Nullable
  private DartCallExpression findEnclosingTestCall(@NotNull PsiElement element, Map<DartCallExpression, TestType> callToTestType) {
    while (element != null) {
      if (element instanceof DartCallExpression) {
        final DartCallExpression call = (DartCallExpression)element;
        if (callToTestType.containsKey(call)) {
          return call;
        }
      }
      element = DartSyntax.findClosestEnclosingFunctionCall(element);
    }
    return null;
  }

  @VisibleForTesting
  protected ActiveEditorsOutlineService getActiveEditorsOutlineService(@NotNull Project project) {
    return ActiveEditorsOutlineService.getInstance(project);
  }
}
