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
import com.jetbrains.lang.dart.util.DartElementGenerator;
import io.flutter.FlutterBundle;
import io.flutter.dart.DartPsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

import static io.flutter.dart.DartPsiUtil.getNewExprFromType;
import static io.flutter.dart.DartPsiUtil.topmostReferenceExpression;

// This class is required to resolve
// https://github.com/flutter/flutter-intellij/issues/5796
// TODO(jacobr): track down a possible bug in Dart PsiElement implementation
// that results in Dart PsiElement objects showing up as equal when they
// are not safe to treat as equal for purposes of resolving PsiElement
// markers. If that issue can be resolved then this hack can be removed.
/**
 * Color class that enables creating colors that are visually identical but
 * are only equal if the colors have the same associated PsiElement.
 * <p>
 * This class is used as a hack to avoid a bug where Dart color icon markers
 * can fail to update after making small code changes that are enough to
 * invalidate the PsiElement but not enough to break equality checks for the
 * PsiElement objects.
 */
class PsiElementColor extends Color {

  PsiElementColor(int r, int g, int b, int a, PsiElement psiElement) {
    super(r, g, b, a);
    this.psiElement = psiElement;
  }

  private PsiElement psiElement;

  public boolean equals(Object obj) {
    if (!(obj instanceof PsiElementColor)) return false;
    PsiElementColor other = (PsiElementColor)obj;
    return other.getRGB() == this.getRGB() && other.psiElement == psiElement;
  }
}

public class FlutterColorProvider implements ElementColorProvider {

  /**
   * When we replace the target PsiElement as part of tweaking a color, we
   * continue to get back the old PsiElement even after it is invalid.
   * To handle this case we track the orignal and replacement elements so
   * that we can swap elements as needed.
   */
  PsiElement originalElement;
  PsiElement replacementElement;

  @Nullable
  @Override
  public Color getColorFrom(@NotNull PsiElement element) {
    final Color color = getColorFromHelper(element);
    if (color == null) return null;
    return new PsiElementColor(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha(), element);
  }

  void cleanupInvalidCache() {
    if (replacementElement != null && !replacementElement.isValid()) {
      originalElement = null;
      replacementElement = null;
    }
  }

  public Color getColorFromHelper(@NotNull PsiElement element) {
    cleanupInvalidCache();
    // This must return null for non-leaf nodes and any language other than Dart.
    if (element.getNode().getElementType() != DartTokenTypes.IDENTIFIER) return null;
    if (element.getFirstChild() != null) return null;

    final String name = element.getText();
    if (!(name.equals("Colors") || name.equals("CupertinoColors") || name.equals("Color"))) return null;

    final PsiElement refExpr = DartPsiUtil.topmostReferenceExpression(element);
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
  public void setColorTo(@NotNull PsiElement targetElement, @NotNull Color color) {
    cleanupInvalidCache();
    PsiElement element;
    if (!targetElement.isValid() && originalElement == targetElement && replacementElement != null) {
      element = replacementElement;
    }
    else {
      element = targetElement;
    }
    if (!element.isValid()) return;

    final Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(element.getContainingFile());
    final Runnable command = () -> {
      final PsiElement refExpr = topmostReferenceExpression(element);
      if (refExpr == null) return;
      PsiElement parent = refExpr.getParent();
      if (parent == null) return;
      final PsiElement targetForReplacement = getTargetForReplacement(element, refExpr);
      if (!element.getText().equals("Color") || FlutterColors.getColorName(color) != null) {
        // Generate an expression for the Color from scratch rather than
        // reusing the existing Color constructor expression because the
        // existing expression is not a color constructor call or there is a
        // Flutter color name that matches the exact color value.
        final String colorExpression = FlutterColors.buildColorExpression(color);
        final PsiFileFactoryImpl factory = new PsiFileFactoryImpl(element.getManager());
        PsiElement newPsi = DartElementGenerator.createExpressionFromText(element.getProject(), colorExpression);
        if (newPsi != null) {
          originalElement = targetElement;
          replacementElement = targetForReplacement.replace(newPsi).getFirstChild().getFirstChild().getFirstChild();
        }
      }
      else if (parent.getNode().getElementType() == DartTokenTypes.CALL_EXPRESSION) {
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

  private PsiElement getTargetForReplacement(PsiElement element, PsiElement refExpr) {
    final String name = element.getText();
    if (element.getText().equals("Color")) {
      // Parent of refExpr is either a CALL_EXPRESSION or SIMPLE_TYPE that creates the instance of the Color object.
      return DartPsiUtil.getSurroundingNewOrCallExpression(refExpr);
    }
    else {
      PsiElement parent = refExpr.getParent();
      // Handle Colors.grey[300] case.
      if (parent != null && parent.getNode().getElementType() == DartTokenTypes.ARRAY_ACCESS_EXPRESSION) return parent;
      // Colors.grey and Colors.grey.shade300 case.
      return refExpr;
    }
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
