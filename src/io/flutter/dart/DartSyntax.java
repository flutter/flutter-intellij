/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.jetbrains.lang.dart.DartTokenTypes;
import com.jetbrains.lang.dart.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Finds Dart PSI elements in IntelliJ's syntax tree.
 */
public class DartSyntax {

  /**
   * Finds the enclosing function call where the function being called has the given name.
   * <p>
   * Returns null if not found.
   */
  @Nullable
  public static DartCallExpression findEnclosingFunctionCall(@NotNull PsiElement elt, @NotNull String functionName) {
    while (elt != null) {
      if (elt instanceof DartCallExpression) {
        final DartCallExpression call = (DartCallExpression)elt;
        final String name = getCalledFunctionName(call);
        if (name != null && name.equals(functionName)) {
          return call;
        }
      }
      elt = elt.getParent();
    }
    return null; // not found
  }

  /**
   * Gets an argument to a function call, provided that the expression has the given type.
   * <p>
   * Returns null if the argument doesn't exist or it's not the given type.
   */
  @Nullable
  public static <E extends DartExpression> E getArgument(@NotNull DartCallExpression call, int index, @NotNull Class<E> expectedClass) {
    if (call.getArguments() == null) return null;

    final DartArgumentList list = call.getArguments().getArgumentList();
    if (list == null) return null;

    final List<DartExpression> args = list.getExpressionList();
    if (args.size() <= index) {
      return null;
    }

    try {
      return expectedClass.cast(args.get(index));
    }
    catch (ClassCastException e) {
      return null;
    }
  }

  /**
   * Check if an element is a call to a function with the given name.
   *
   * @return true if the given element is a call to function, false otherwise
   */
  public static boolean isCallToFunctionNamed(@NotNull DartCallExpression element, @NotNull String function) {
    final String name = getCalledFunctionName(element);
    return Objects.equals(name, function);
  }

  /**
   * Check if an element is a declaration of "main".
   *
   * @return true if the given element is a main declaration, false otherwise
   */
  public static boolean isMainFunctionDeclaration(@Nullable PsiElement element) {
    if (!(element instanceof DartFunctionDeclarationWithBodyOrNative)) return false;
    final String functionName = ((DartFunctionDeclarationWithBodyOrNative)element).getComponentName().getId().getText();
    return Objects.equals(functionName, "main");
  }

  /**
   * Returns the contents of a Dart string literal, provided that it doesn't do any interpolation.
   * <p>
   * Returns null if there is any string interpolation.
   */
  @Nullable
  public static String unquote(@NotNull DartStringLiteralExpression lit) {
    if (!lit.getShortTemplateEntryList().isEmpty() || !lit.getLongTemplateEntryList().isEmpty()) {
      return null; // is a template
    }
    // We expect a quote, string part, quote.
    if (lit.getFirstChild() == null) return null;
    final PsiElement second = lit.getFirstChild().getNextSibling();
    if (second.getNextSibling() != lit.getLastChild()) return null; // not three items
    if (!(second instanceof LeafPsiElement)) return null;
    final LeafPsiElement leaf = (LeafPsiElement)second;

    if (leaf.getElementType() != DartTokenTypes.REGULAR_STRING_PART) return null;
    return leaf.getText();
  }

  @Nullable
  private static String getCalledFunctionName(@NotNull DartCallExpression call) {
    if (!(call.getFirstChild() instanceof DartReference)) return null;
    return call.getFirstChild().getText();
  }

}
