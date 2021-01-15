/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import com.intellij.psi.PsiElement;
import com.jetbrains.lang.dart.DartTokenTypes;
import com.jetbrains.lang.dart.psi.DartArgumentList;
import com.jetbrains.lang.dart.psi.DartArguments;
import com.jetbrains.lang.dart.psi.DartExpression;
import com.jetbrains.lang.dart.psi.DartNamedArgument;
import com.jetbrains.lang.dart.util.DartPsiImplUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DartPsiUtil {

  public static int parseLiteralNumber(@NotNull String val) {
    return val.startsWith("0x") || val.startsWith("0X")
           ? Integer.parseUnsignedInt(val.substring(2), 16)
           : Integer.parseUnsignedInt(val);
  }

  @Nullable
  public static PsiElement getNewExprFromType(PsiElement element) {
    if (element.getNode().getElementType() != DartTokenTypes.SIMPLE_TYPE) return null;
    PsiElement parent = element.getParent();
    if (parent == null) return null;
    parent = parent.getParent();
    if (parent == null) return null;
    if (parent.getNode().getElementType() != DartTokenTypes.NEW_EXPRESSION) return null;
    return parent;
  }

  @Nullable
  public static String getValueOfPositionalArgument(@NotNull DartArguments arguments, int index) {
    final DartExpression expression = getPositionalArgument(arguments, index);
    if (expression == null) return null;
    if (expression.getNode().getElementType() != DartTokenTypes.LITERAL_EXPRESSION) return null;
    return expression.getText();
  }

  @Nullable
  public static DartExpression getPositionalArgument(@NotNull DartArguments arguments, int index) {
    final DartArgumentList list = arguments.getArgumentList();
    if (list == null) return null;
    if (index >= list.getExpressionList().size()) return null;
    return list.getExpressionList().get(index);
  }

  @Nullable
  public static String getValueOfNamedArgument(@NotNull DartArguments arguments, @NotNull String name) {
    final PsiElement family = getNamedArgumentExpression(arguments, "fontFamily");
    if (family != null && family.getNode().getElementType() == DartTokenTypes.STRING_LITERAL_EXPRESSION) {
      return DartPsiImplUtil.getUnquotedDartStringAndItsRange(family.getText()).first;
    }
    return null;
  }

  @Nullable
  public static PsiElement getNamedArgumentExpression(@NotNull DartArguments arguments, @NotNull String name) {
    final DartArgumentList list = arguments.getArgumentList();
    if (list == null) return null;
    final List<DartNamedArgument> namedArgumentList = list.getNamedArgumentList();
    for (DartNamedArgument namedArgument : namedArgumentList) {
      final DartExpression nameExpression = namedArgument.getParameterReferenceExpression();
      final PsiElement childId = nameExpression.getFirstChild();
      final PsiElement child = nameExpression.getFirstChild();
      if (name.equals(child.getText())) {
        return namedArgument.getExpression();
      }
    }
    return null;
  }

  @Nullable
  public static PsiElement topmostReferenceExpression(@NotNull PsiElement element) {
    final PsiElement id = element.getParent();
    if (id == null || id.getNode().getElementType() != DartTokenTypes.ID) return null;
    PsiElement refExpr = id.getParent();
    if (refExpr == null || refExpr.getNode().getElementType() != DartTokenTypes.REFERENCE_EXPRESSION) return null;

    PsiElement parent = refExpr.getParent();
    while (parent != null && parent.getNode().getElementType() == DartTokenTypes.REFERENCE_EXPRESSION) {
      refExpr = parent;
      parent = parent.getParent();
    }
    return refExpr;
  }
}
