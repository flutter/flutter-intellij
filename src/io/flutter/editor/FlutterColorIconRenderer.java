/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.util.ui.ColorIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

// TODO: click actions?

// TODO: tooltips

public class FlutterColorIconRenderer extends GutterIconRenderer implements DumbAware {
  private final ColorIcon myIcon;
  private final PsiElement myElement;

  public FlutterColorIconRenderer(ColorIcon icon, PsiElement element) {
    myIcon = icon;
    myElement = element;
  }

  public String getTooltipText() {
    return myElement.getText();
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return myIcon;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FlutterColorIconRenderer renderer = (FlutterColorIconRenderer)o;

    if (myElement != null ? !myElement.equals(renderer.myElement) : renderer.myElement != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myElement.hashCode();
  }
}