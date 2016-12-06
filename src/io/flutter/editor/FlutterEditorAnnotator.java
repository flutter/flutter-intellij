/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.util.ui.ColorIcon;
import com.jetbrains.lang.dart.psi.DartArrayAccessExpression;
import com.jetbrains.lang.dart.psi.DartReferenceExpression;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Properties;

// Support Icons.add
// Support Colors.white70
// Support Colors.red[400]

// TODO(devoncarew): Support const IconData(0xe145)
// TODO(devoncarew): Support const Color(0x4DFFFFFF)

public class FlutterEditorAnnotator implements Annotator {
  private static final Logger LOG = Logger.getInstance(FlutterEditorAnnotator.class);

  private static final Properties colors;
  private static final Properties icons;

  static {
    colors = new Properties();
    icons = new Properties();

    try {
      colors.load(FlutterEditorAnnotator.class.getResourceAsStream("/flutter/colors.properties"));
    }
    catch (IOException e) {
      LOG.warn(e);
    }

    try {
      icons.load(FlutterEditorAnnotator.class.getResourceAsStream("/flutter/icons.properties"));
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (holder.isBatchMode()) return;

    // TODO: Don't try and annotate when indexing.
    // TODO: What are the performance implications of element.getText()?
    // TODO: Use a DartVisitor instead?

    if (element instanceof DartReferenceExpression || element instanceof DartArrayAccessExpression) {
      if (!isInFlutterModule(element)) {
        return;
      }

      String text = element.getText();

      // TODO: Make this more efficient.

      if (text.startsWith("Colors.")) {
        text = text.substring("Colors.".length());
        if (colors.containsKey(text)) {
          final Color color = getColor(text);
          if (color != null) {
            attachColorIcon(element, holder, color);
          }
        }
      }
      else if (text.startsWith("Icons.")) {
        text = text.substring("Icons.".length());
        if (icons.containsKey(text)) {
          final Icon icon = getIcon(text);
          if (icon != null) {
            attachIcon(element, holder, icon);
          }
        }
      }
    }
  }

  private boolean isInFlutterModule(@NotNull PsiElement element) {
    return FlutterSdkUtil.isFlutterModule(ModuleUtil.findModuleForPsiElement(element));
  }

  private Icon getIcon(String id) {
    final String path = icons.getProperty(id);
    if (path == null) {
      return null;
    }
    return IconLoader.findIcon(path, FlutterEditorAnnotator.class);
  }

  private static Color getColor(String name) {
    try {
      final String hexValue = colors.getProperty(name);
      if (hexValue == null) {
        return null;
      }

      // argb to r, g, b, a
      final long value = Long.parseLong(hexValue, 16);
      //noinspection UseJBColor
      return new Color((int)(value >> 16) & 0xFF, (int)(value >> 8) & 0xFF, (int)value & 0xFF, (int)(value >> 24) & 0xFF);
    }
    catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static void attachColorIcon(final PsiElement element, AnnotationHolder holder, Color color) {
    attachIcon(element, holder, new ColorIcon(10, color));
  }

  private static void attachIcon(final PsiElement element, AnnotationHolder holder, Icon icon) {
    try {
      final Annotation annotation = holder.createInfoAnnotation(element, null);
      annotation.setGutterIconRenderer(new FlutterIconRenderer(icon, element));
    }
    catch (Exception ignored) {
    }
  }
}
