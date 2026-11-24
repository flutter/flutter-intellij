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

/**
 * Live template context for top-level Dart declarations outside of class bodies and comments.
 *
 * <p>Registered declaratively in {@code plugin.xml} with {@code contextId="DART_TOPLEVEL"} and
 * {@code baseContextId="DART"}.
 */
public class DartToplevelTemplateContextType extends DartTemplateContextType {
  /**
   * Creates the {@code DART_TOPLEVEL} template context type with presentable name {@code "Top-level"}.
   */
  public DartToplevelTemplateContextType() {
    super("Top-level");
  }

  @Override
  protected boolean isInContext(@NotNull PsiElement element) {
    return PsiTreeUtil.getNonStrictParentOfType(element, DartClassDefinition.class, PsiComment.class) == null;
  }
}
