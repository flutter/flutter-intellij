/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Objects;

public class FlutterIconRenderer extends GutterIconRenderer implements DumbAware {
  private final Icon myIcon;
  private final String myId;

  public FlutterIconRenderer(Icon icon, PsiElement element) {
    myIcon = icon;
    myId = element.getText();
  }

  public String getTooltipText() {
    return myId;
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

    final FlutterIconRenderer renderer = (FlutterIconRenderer)o;
    return Objects.equals(myId, renderer.myId);
  }

  @Override
  public int hashCode() {
    return myId.hashCode();
  }
}
