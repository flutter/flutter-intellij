/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import com.jetbrains.lang.dart.ide.completion.DartCompletionExtension;
import com.jetbrains.lang.dart.ide.completion.DartServerCompletionContributor;
import org.apache.commons.lang.StringUtils;
import org.dartlang.analysis.server.protocol.CompletionSuggestion;
import org.dartlang.analysis.server.protocol.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;


public class FlutterCompletionContributor extends DartCompletionExtension {

  private static final int ICON_SIZE = 16;
  private static final Icon EMPTY_ICON = JBUI.scale(EmptyIcon.create(ICON_SIZE));

  @Override
  @Nullable
  public LookupElementBuilder createLookupElement(@NotNull final Project project, @NotNull final CompletionSuggestion suggestion) {
    final Icon icon = findIcon(suggestion);
    if (icon != null) {
      final LookupElementBuilder lookup = DartServerCompletionContributor.createLookupElement(project, suggestion).withTypeText("", icon, false);

      // 2018.1 introduces a new API to specify right alignment for type icons (the default previously).
      // TODO(pq): remove reflective access when 2018.1 is our minimum.
      final Method rightAligned = ReflectionUtil.getMethod(lookup.getClass(), "withTypeIconRightAligned");
      if (rightAligned != null) {
        try {
          return (LookupElementBuilder)rightAligned.invoke(lookup, true);
        }
        catch (IllegalAccessException | InvocationTargetException e) {
          // Shouldn't happen but if it does fall back on default.
        }
      }
      return lookup;
    }
    return null;
  }

  private static Icon findIcon(@NotNull final CompletionSuggestion suggestion) {
    final Element element = suggestion.getElement();
    if (element != null) {
      final String returnType = element.getReturnType();
      if (!StringUtils.isEmpty(returnType)) {
        final String name = element.getName();
        if (name != null) {
          final String declaringType = suggestion.getDeclaringType();
          if (Objects.equals(declaringType, "Colors")) {
            final FlutterColors.FlutterColor color = FlutterColors.getColor(name);
            if (color != null) {
              return JBUI.scale(new ColorIcon(ICON_SIZE, color.getAWTColor()));
            }
          }
          else if (Objects.equals(declaringType, "Icons")) {
            final Icon icon = FlutterMaterialIcons.getMaterialIconForName(name);
            // If we have no icon, show an empty node (which is preferable to the default "IconData" text).
            return  icon != null ? icon : EMPTY_ICON;
          }
        }
      }
    }

    return null;
  }
}
