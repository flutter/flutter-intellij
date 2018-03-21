/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.JBUI;
import com.jetbrains.lang.dart.ide.completion.DartCompletionExtension;
import com.jetbrains.lang.dart.ide.completion.DartServerCompletionContributor;
import org.apache.commons.lang.StringUtils;
import org.dartlang.analysis.server.protocol.CompletionSuggestion;
import org.dartlang.analysis.server.protocol.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;


public class FlutterCompletionContributor extends DartCompletionExtension {

  @Nullable
  public LookupElementBuilder createLookupElement(@NotNull final Project project, @NotNull final CompletionSuggestion suggestion) {
    final Icon icon = findIcon(suggestion);
    if (icon != null) {
      final LookupElementBuilder lookup = DartServerCompletionContributor.createLookupElement(project, suggestion);
      // In 2018.1:
      // lookup.withTypeText("", icon, false).withTypeIconRightAligned(true);
      // TODO(pq): consider using reflection to invoke if available.
      return lookup.withTypeText("", icon, false); //
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
            FlutterColors.FlutterColor color = FlutterColors.getColor(name);
            if (color != null) {
              return JBUI.scale(new ColorIcon(15, color.getAWTColor()));
            }
          }
          else if (Objects.equals(declaringType, "Icons")) {
            return FlutterMaterialIcons.getMaterialIconForName(name);
          }
        }
      }
    }

    return null;
  }
}
