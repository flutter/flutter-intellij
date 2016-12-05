/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.ColorIcon;
import com.jetbrains.lang.dart.DartTokenTypes;
import com.jetbrains.lang.dart.psi.DartReferenceExpression;
import org.apache.velocity.runtime.parser.node.ASTNENode;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

// TODO: Look for flutter material icons (Icons.add)
//   (https://github.com/flutter/flutter/wiki/Updating-Material-Design-Fonts)
// TODO: support color swatches (Colors.white70, Colors.red[400])
// TODO: also color definitions ( const Color(0x4DFFFFFF) )

// TODO: We want to makde sure the 'Colors' or 'Icons' references resolves to a Flutter one.
//    use the file imports?

public class FlutterEditorAnnotator implements Annotator {
  private static final Map<String, String> kColorMap = new HashMap<>();

  static {
    kColorMap.put("transparent", "000000");
    kColorMap.put("black", "000000");
    kColorMap.put("black87", "000000");
    kColorMap.put("black54", "000000");
    kColorMap.put("black38", "000000");
    kColorMap.put("black45", "000000");
    kColorMap.put("black26", "000000");
    kColorMap.put("black12", "000000");
    kColorMap.put("white", "FFFFFF");
    kColorMap.put("white70", "FFFFFF");
    kColorMap.put("white30", "FFFFFF");
    kColorMap.put("white12", "FFFFFF");
    kColorMap.put("white10", "FFFFFF");
    kColorMap.put("redAccent", "FF8A80");
  }

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (holder.isBatchMode()) return;

    // TODO: flutter module/project

    final PsiFile containingFile = element.getContainingFile();

    if (element instanceof DartReferenceExpression) {
      DartReferenceExpression refExpression = (DartReferenceExpression)element;

      if (isRef(refExpression.getTokenType())) {
        ASTNode node = refExpression.getNode();

        if (node instanceof CompositeElement) {
          CompositeElement cNode = (CompositeElement)node;
          TreeElement firstChild = cNode.getFirstChildNode();

          if (isRef(firstChild) && isIdentifier(firstChild.getFirstChildNode(), "Colors")) {
            if (isPeriod(firstChild.getTreeNext()) && isRef(firstChild.getTreeNext().getTreeNext())) {
              if (firstChild.getTreeNext().getTreeNext() == cNode.getLastChildNode()) {
                TreeElement idNode = cNode.getLastChildNode();

                if (idNode.getFirstChildNode() == idNode.getLastChildNode() && isIdentifier(idNode.getFirstChildNode())) {
                  String fullText = cNode.getText();
                  String id = idNode.getText();

                  String colorValue = kColorMap.get(id);

                  if (colorValue != null) {
                    attachColorIcon(element, holder, colorValue);
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  private boolean isRef(TreeElement element) {
    return element != null && element.getElementType() == DartTokenTypes.REFERENCE_EXPRESSION;
  }

  private boolean isRef(IElementType elementType) {
    return elementType == DartTokenTypes.REFERENCE_EXPRESSION;
  }

  private boolean isPeriod(TreeElement element) {
    return element != null && element.getElementType() == DartTokenTypes.DOT;
  }

  private boolean isIdentifier(IElementType elementType) {
    return elementType == DartTokenTypes.IDENTIFIER;
  }

  private boolean isIdentifier(TreeElement element) {
    return element != null && element.getElementType() == DartTokenTypes.ID;
  }

  private boolean isIdentifier(TreeElement element, String name) {
    return element != null && element.getElementType() == DartTokenTypes.ID && name.equals(element.getText());
  }

  private static void attachColorIcon(final PsiElement element, AnnotationHolder holder, String valueText) {
    try {
      Color color = ColorUtil.fromHex(valueText);
      // TODO: scaling?
      final ColorIcon icon = new ColorIcon(8, color);
      final Annotation annotation = holder.createInfoAnnotation(element, null);
      annotation.setGutterIconRenderer(new FlutterColorIconRenderer(icon, element));
    }
    catch (Exception ignored) {
    }
  }
}
