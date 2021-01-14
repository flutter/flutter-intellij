/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.editor.ElementColorProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.AstBufferUtil;
import com.jetbrains.lang.dart.DartTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static io.flutter.dart.DartPsiUtil.getNewExprFromType;
import static io.flutter.dart.DartPsiUtil.topmostReferenceExpression;

public class FlutterColorProvider implements ElementColorProvider {
  @Nullable
  @Override
  public Color getColorFrom(@NotNull PsiElement element) {
    // This must return null for non-leaf nodes and any language other than Dart.
    if (element.getNode().getElementType() != DartTokenTypes.IDENTIFIER) return null;

    final String name = element.getText();
    if (!(name.equals("Colors") || name.equals("CupertinoColors") || name.equals("Color"))) return null;

    final PsiElement refExpr = topmostReferenceExpression(element);
    if (refExpr == null) return null;
    PsiElement parent = refExpr.getParent();
    if (parent == null) return null;

    if (parent.getNode().getElementType() == DartTokenTypes.ARRAY_ACCESS_EXPRESSION) {
      // Colors.blue[200]
      final String code = AstBufferUtil.getTextSkippingWhitespaceComments(parent.getNode());
      return parseColorText(code.substring(code.indexOf(name) + name.length() + 1), name);
    }
    else if (parent.getNode().getElementType() == DartTokenTypes.CALL_EXPRESSION) {
      // foo(Color.fromRGBO(0, 255, 0, 0.5))
      return parseColorElements(parent, refExpr);
    }
    else if (parent.getNode().getElementType() == DartTokenTypes.SIMPLE_TYPE) {
      // const Color.fromARGB(100, 255, 0, 0)
      // parent.getParent().getParent() is a new expr
      parent = getNewExprFromType(parent);
      if (parent == null) return null;

      return parseColorElements(parent, refExpr);
    }
    else {
      // name.equals(refExpr.getFirstChild().getText()) -> Colors.blue
      final PsiElement idNode = refExpr.getFirstChild();
      if (idNode == null) return null;
      if (name.equals(idNode.getText())) {
        final PsiElement selectorNode = refExpr.getLastChild();
        if (selectorNode == null) return null;
        final String code = AstBufferUtil.getTextSkippingWhitespaceComments(selectorNode.getNode());
        return parseColorText(code, name);
      }
      // refExpr.getLastChild().getText().startsWith("shade") -> Colors.blue.shade200
      final PsiElement child = refExpr.getLastChild();
      if (child == null) return null;
      if (child.getText().startsWith("shade")) {
        final String code = AstBufferUtil.getTextSkippingWhitespaceComments(refExpr.getNode());
        return parseColorText(code.substring(code.indexOf(name) + name.length() + 1), name);
      }
    }
    return null;
  }

  @Nullable
  private Color parseColorElements(@NotNull PsiElement parent, @NotNull PsiElement refExpr) {
    final PsiElement selectorNode = refExpr.getLastChild();
    if (selectorNode == null) return null;
    final String selector = selectorNode.getText();
    final boolean isFromARGB = "fromARGB".equals(selector);
    final boolean isFromRGBO = "fromRGBO".equals(selector);
    if (isFromARGB || isFromRGBO) {
      String code = AstBufferUtil.getTextSkippingWhitespaceComments(parent.getNode());
      if (code.startsWith("constColor(")) {
        code = code.substring(5);
      }
      return ExpressionParsingUtils.parseColorComponents(code.substring(code.indexOf(selector)), selector + "(", isFromARGB);
    }
    final PsiElement args = parent.getLastChild();
    if (args != null && args.getNode().getElementType() == DartTokenTypes.ARGUMENTS) {
      String code = AstBufferUtil.getTextSkippingWhitespaceComments(parent.getNode());
      if (code.startsWith("constColor(")) {
        code = code.substring(5);
      }
      return ExpressionParsingUtils.parseColor(code);
    }
    return null;
  }

  @Nullable
  private Color parseColorText(@NotNull String text, @NotNull String platform) {
    final FlutterColors.FlutterColor color;
    if ("CupertinoColors".equals(platform)) {
      color = FlutterCupertinoColors.getColor(text);
    } else {
      color = FlutterColors.getColor(text);
    }
    if (color != null) {
      return color.getAWTColor();
    }
    return null;
  }

  @Override
  public void setColorTo(@NotNull PsiElement element, @NotNull Color color) {
    // This does not work because MaterialColor requires a fully specified swatch map.
    //final Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(element.getContainingFile());
    //final Runnable command = () -> {
    //  final PsiFileFactoryImpl factory = new PsiFileFactoryImpl(element.getManager());
    //  String a = Integer.toHexString(color.getAlpha());
    //  String r = Integer.toHexString(color.getRed());
    //  String b = Integer.toHexString(color.getBlue());
    //  String g = Integer.toHexString(color.getGreen());
    //  String newExpr = "MaterialColor(0x" + a + r + b + g + ", {})";
    //  PsiElement newPsi =
    //    factory.createElementFromText(newExpr, DartLanguage.INSTANCE, DartTokenTypes.NEW_EXPRESSION, element.getContext());
    //  if (newPsi != null) {
    //    element.replace(newPsi);
    //  }
    //};
    //CommandProcessor.getInstance()
    //  .executeCommand(element.getProject(), command, FlutterBundle.message("change.color.command.text"), null, document);
  }
}
