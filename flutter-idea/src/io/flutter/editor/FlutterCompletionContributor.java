/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.EmptyIcon;
import com.jetbrains.lang.dart.ide.completion.DartCompletionExtension;
import com.jetbrains.lang.dart.ide.completion.DartServerCompletionContributor;
import io.flutter.sdk.FlutterSdk;
import org.apache.commons.lang.StringUtils;
import org.dartlang.analysis.server.protocol.CompletionSuggestion;
import org.dartlang.analysis.server.protocol.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

public class FlutterCompletionContributor extends DartCompletionExtension {
  private static final int ICON_SIZE = 16;
  private static final Icon EMPTY_ICON = EmptyIcon.create(ICON_SIZE);

  @Override
  @Nullable
  public LookupElementBuilder createLookupElement(@NotNull final Project project, @NotNull final CompletionSuggestion suggestion) {
    final Icon icon = findIcon(suggestion, project);
    if (icon != null) {
      final LookupElementBuilder lookup =
        DartServerCompletionContributor.createLookupElement(project, suggestion).withTypeText("", icon, false);
      // Specify right alignment for type icons.
      return lookup.withTypeIconRightAligned(true);
    }

    return null;
  }

  @Nullable
  private static Icon findIcon(@NotNull final CompletionSuggestion suggestion, @NotNull Project project) {
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
              return new ColorIcon(ICON_SIZE, color.getAWTColor());
            }
          }
          else if (Objects.equals(declaringType, "CupertinoColors")) {
            final FlutterColors.FlutterColor color = FlutterCupertinoColors.getColor(name);
            if (color != null) {
              return new ColorIcon(ICON_SIZE, color.getAWTColor());
            }
          }
          else if (Objects.equals(declaringType, "Icons")) {
            final Icon icon = FlutterIconLineMarkerProvider.getMaterialIconByName(project, getSdkHomePath(project), name);
            // If we have no icon, show an empty node (which is preferable to the default "IconData" text).
            return icon != null ? icon : EMPTY_ICON;
          }
          else if (Objects.equals(declaringType, "CupertinoIcons")) {
            final Icon icon = FlutterIconLineMarkerProvider.getCupertinoIconByName(project, getSdkHomePath(project), name);
            // If we have no icon, show an empty node (which is preferable to the default "IconData" text).
            return icon != null ? icon : EMPTY_ICON;
          }
        }
      }
    }

    return null;
  }

  @NotNull
  private static String getSdkHomePath(@NotNull Project project) {
    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null) {
      assert ApplicationManager.getApplication() != null;
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        return "testData/sdk";
      }
      throw new NullPointerException("Flutter SDK not found");
    }
    return sdk.getHomePath();
  }
}
