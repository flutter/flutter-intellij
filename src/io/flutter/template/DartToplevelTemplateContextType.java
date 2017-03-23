/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.template;

import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.lang.dart.ide.template.DartTemplateContextType;
import com.jetbrains.lang.dart.psi.DartClassDefinition;
import org.jetbrains.annotations.NotNull;

public class DartToplevelTemplateContextType extends DartTemplateContextType {
  public DartToplevelTemplateContextType() {
    super("DART_TOPLEVEL", "Top-level", Generic.class);
  }

  @Override
  protected boolean isInContext(@NotNull PsiElement element) {
    //noinspection unchecked
    return PsiTreeUtil.getNonStrictParentOfType(element, DartClassDefinition.class, PsiComment.class) == null;
  }
}
