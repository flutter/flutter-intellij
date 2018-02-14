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
import com.intellij.psi.PsiElement;
import com.intellij.util.ui.ColorIcon;
import com.jetbrains.lang.dart.psi.DartArrayAccessExpression;
import com.jetbrains.lang.dart.psi.DartNewExpression;
import com.jetbrains.lang.dart.psi.DartReferenceExpression;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Properties;

import static io.flutter.utils.FlutterModuleUtils.isInFlutterModule;

/**
 * Add Material icons and Flutter color icons to the editor's gutter.
 */
public class FlutterEditorAnnotator implements Annotator {
  private static final Logger LOG = Logger.getInstance(FlutterEditorAnnotator.class);

  private static final Properties colors;

  static {
    colors = new Properties();

    try {
      colors.load(FlutterEditorAnnotator.class.getResourceAsStream("/flutter/colors.properties"));
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

  @Override
  public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
    if (holder.isBatchMode()) return;

    if (element instanceof DartReferenceExpression || element instanceof DartArrayAccessExpression) {
      if (!isInFlutterModule(element)) {
        return;
      }

      final String text = element.getText();

      if (text.startsWith("Colors.")) {
        String key = text.substring("Colors.".length());

        // Handle things like Colors.blue.shade200; convert the text to blue[200].
        if (key.contains(".shade")) {
          key = key.replace(".shade", "[") + "]";
        }

        if (colors.containsKey(key)) {
          final Color color = getColor(key);
          if (color != null) {
            attachColorIcon(element, holder, color);
          }
        }
        else if (colors.containsKey(key + ".primary")) {
          // If we're a primary color access, and
          // - we're not followed by an array access (really referencing a more specific color)
          // - we're not followed by a shadeXXX access
          final boolean inColorIndexExpression = element.getParent() instanceof DartArrayAccessExpression;
          final boolean inShadeExpression =
            (element.getParent() instanceof DartReferenceExpression && element.getParent().getText().startsWith(text + ".shade"));
          if (!inShadeExpression && !inColorIndexExpression) {
            final Color color = getColor(key + ".primary");
            if (color != null) {
              attachColorIcon(element, holder, color);
            }
          }
        }
      }
      else if (text.startsWith("Icons.")) {
        final String key = text.substring("Icons.".length());
        final Icon icon = FlutterMaterialIcons.getMaterialIconForName(key);
        if (icon != null) {
          attachIcon(element, holder, icon);
        }
      }
    }
    else if (element instanceof DartNewExpression) {
      // const IconData(0xe914)
      final String text = element.getText();

      if (text.startsWith("const IconData(") && text.endsWith(")")) {
        String val = text.substring("const IconData(".length());
        val = val.substring(0, val.length() - 1);
        final int index = val.indexOf(',');
        if (index != -1) {
          val = val.substring(0, index);
        }
        try {
          final int value = val.startsWith("0x")
                            ? Integer.parseInt(val.substring(2), 16)
                            : Integer.parseInt(val);
          final String hex = Integer.toHexString(value);
          final Icon icon = FlutterMaterialIcons.getMaterialIconForHex(hex);
          if (icon != null) {
            attachIcon(element, holder, icon);
          }
        }
        catch (NumberFormatException ignored) {
        }
      }
      else if (text.startsWith("const Color(") && text.endsWith(")")) {
        String val = text.substring("const Color(".length());
        val = val.substring(0, val.length() - 1);
        try {
          final long value = val.startsWith("0x")
                             ? Long.parseLong(val.substring(2), 16)
                             : Long.parseLong(val);
          //noinspection UseJBColor
          final Color color = new Color((int)(value >> 16) & 0xFF, (int)(value >> 8) & 0xFF, (int)value & 0xFF, (int)(value >> 24) & 0xFF);
          attachColorIcon(element, holder, color);
        }
        catch (NumberFormatException ignored) {
        }
      }
    }
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
    attachIcon(element, holder, new ColorIcon(16, 12, color, true));
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
