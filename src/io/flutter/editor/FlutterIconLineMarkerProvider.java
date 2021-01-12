/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.codeInsight.daemon.GutterName;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.psi.PsiElement;
import com.jetbrains.lang.dart.DartTokenTypes;
import com.jetbrains.lang.dart.psi.*;
import com.jetbrains.lang.dart.util.DartPsiImplUtil;
import io.flutter.FlutterBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FlutterIconLineMarkerProvider extends LineMarkerProviderDescriptor {
  @Nullable("null means disabled")
  @Override
  public @GutterName String getName() {
    return FlutterBundle.message("fluitter.icon.preview.title");
  }

  @Override
  public LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
    if (element.getNode().getElementType() != DartTokenTypes.IDENTIFIER) return null;

    final String name = element.getText();
    if (!(name.equals("Icons") || name.equals("CupertinoIcons") || name.equals("IconData"))) return null;

    final PsiElement refExpr = FlutterColorProvider.topmostReferenceExpression(element);
    if (refExpr == null) return null;
    PsiElement parent = refExpr.getParent();
    if (parent == null) return null;

    if (parent.getNode().getElementType() == DartTokenTypes.CALL_EXPRESSION) {
      // Check font family and package
      final DartArguments arguments = DartPsiImplUtil.getArguments((DartCallExpression)parent);
      if (arguments == null) return null;
      final PsiElement family = getNamedArgumentExpression(arguments, "fontFamily");
      if (family != null && family.getNode().getElementType() == DartTokenTypes.STRING_LITERAL_EXPRESSION) {
        final String text = DartPsiImplUtil.getUnquotedDartStringAndItsRange(family.getText()).first;
        if ("MaterialIcons".equals(text)) return null; // TODO https://github.com/flutter/flutter-intellij/issues/2334
      }
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
}
