/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ElementColorProvider;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.PsiFileFactoryImpl;
import com.intellij.psi.impl.source.tree.AstBufferUtil;
import com.jetbrains.lang.dart.DartLanguage;
import com.jetbrains.lang.dart.DartTokenTypes;
import com.jetbrains.lang.dart.psi.DartArgumentList;
import com.jetbrains.lang.dart.psi.DartArguments;
import com.jetbrains.lang.dart.psi.DartExpression;
import com.jetbrains.lang.dart.psi.DartLiteralExpression;
import io.flutter.FlutterBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

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
    }
    else {
      color = FlutterColors.getColor(text);
    }
    if (color != null) {
      return color.getAWTColor();
    }
    return null;
  }

  @Override
  public void setColorTo(@NotNull PsiElement element, @NotNull Color color) {
    // Not trying to look up Material or Cupertino colors.
    // Unfortunately, there is no way to prevent the color picker from showing (if clicked) for those expressions.
    if (!element.getText().equals("Color")) return;
    final Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(element.getContainingFile());
    final Runnable command = () -> {
      final PsiElement refExpr = topmostReferenceExpression(element);
      if (refExpr == null) return;
      PsiElement parent = refExpr.getParent();
      if (parent == null) return;
      if (parent.getNode().getElementType() == DartTokenTypes.CALL_EXPRESSION) {
        // foo(Color.fromRGBO(0, 255, 0, 0.5))
        replaceColor(parent, refExpr, color);
      }
      else if (parent.getNode().getElementType() == DartTokenTypes.SIMPLE_TYPE) {
        // const Color.fromARGB(100, 255, 0, 0)
        // parent.getParent().getParent() is a new expr
        parent = getNewExprFromType(parent);
        if (parent == null) return;
        replaceColor(parent, refExpr, color);
      }
    };
    CommandProcessor.getInstance()
      .executeCommand(element.getProject(), command, FlutterBundle.message("change.color.command.text"), null, document);
  }

  private void replaceColor(@NotNull PsiElement parent, @NotNull PsiElement refExpr, Color color) {
    final PsiElement selectorNode = refExpr.getLastChild();
    if (selectorNode == null) return;
    final String selector = selectorNode.getText();
    final boolean isFromARGB = "fromARGB".equals(selector);
    final boolean isFromRGBO = "fromRGBO".equals(selector);
    final PsiElement args = parent.getLastChild();
    if (args == null || args.getNode().getElementType() != DartTokenTypes.ARGUMENTS) return;
    final DartArgumentList list = ((DartArguments)args).getArgumentList();
    if (list == null) return;
    if (isFromARGB) {
      replaceARGB(list.getExpressionList(), color);
    }
    else if (isFromRGBO) {
      replaceRGBO(list.getExpressionList(), color);
    }
    else {
      replaceArg(list.getExpressionList(), color);
    }
  }

  private void replaceARGB(@NotNull List<DartExpression> args, Color color) {
    if (args.size() != 4) return;
    final List<Integer> colors = Arrays.asList(color.getAlpha(), color.getRed(), color.getGreen(), color.getBlue());
    for (int i = 0; i < args.size(); i++) {
      replaceInt(args.get(i), colors.get(i));
    }
  }

  private void replaceRGBO(@NotNull List<DartExpression> args, Color color) {
    if (args.size() != 4) return;
    final List<Integer> colors = Arrays.asList(color.getRed(), color.getGreen(), color.getBlue());
    for (int i = 0; i < colors.size(); i++) {
      replaceInt(args.get(i), colors.get(i));
    }
    replaceDouble(args.get(3), (double)color.getAlpha() / 255.0);
  }

  private void replaceArg(@NotNull List<DartExpression> args, Color color) {
    if (args.size() != 1) return;
    replaceInt(args.get(0), color.getRGB());
  }

  private void replaceInt(DartExpression expr, Integer value) {
    if (expr instanceof DartLiteralExpression) {
      final String source = expr.getText();
      final String number = source.substring(Math.min(2, source.length()));
      // Preserve case of 0x separate from hex string, eg. 0xFFEE00DD.
      final boolean isHex = source.startsWith("0x") || source.startsWith("0X");
      final boolean isUpper = isHex && number.toUpperCase().equals(number);
      final String newValue = isHex ? Integer.toHexString(value) : Integer.toString(value);
      final String num = isUpper ? newValue.toUpperCase() : newValue;
      final String hex = isHex ? source.substring(0, 2) + num : num;
      final PsiFileFactoryImpl factory = new PsiFileFactoryImpl(expr.getManager());
      final PsiElement newPsi =
        factory.createElementFromText(hex, DartLanguage.INSTANCE, DartTokenTypes.LITERAL_EXPRESSION, expr.getContext());
      if (newPsi != null) {
        expr.replace(newPsi);
      }
    }
  }

  private void replaceDouble(DartExpression expr, Double value) {
    if (expr instanceof DartLiteralExpression) {
      final PsiFileFactoryImpl factory = new PsiFileFactoryImpl(expr.getManager());
      final String number = Double.toString(value);
      final PsiElement newPsi =
        factory.createElementFromText(number, DartLanguage.INSTANCE, DartTokenTypes.LITERAL_EXPRESSION, expr.getContext());
      if (newPsi != null) {
        expr.replace(newPsi);
      }
    }
  }
}
