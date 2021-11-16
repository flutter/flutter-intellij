/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.samples;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.lang.dart.psi.DartComponent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class DartDocumentUtils {
  private DartDocumentUtils() {
  }

  /**
   * Given a document and a Dart PSI element, return the dartdoc text, if any, associated with the Dart
   * symbol definition.
   * <p>
   * If no dartdoc is found, an empty list is returned.
   */
  @NotNull
  public static List<String> getDartdocFor(@NotNull Document document, @NotNull DartComponent component) {
    final List<String> lines = new ArrayList<>();

    final int startLine = document.getLineNumber(component.getTextOffset());

    int line = startLine - 1;

    // Look for any lines between the dartdoc comment and the class declaration.
    while (line >= 0) {
      final String text = StringUtil.trimLeading(getLine(document, line));

      // blank lines and annotations are ok
      if (text.isEmpty() || text.startsWith("@")) {
        line--;
        continue;
      }

      // dartdoc comments move us to the next state
      if (text.startsWith("///")) {
        lines.add(text);
        line--;

        break;
      }

      // anything else means we didn't find a dartdoc comment
      return lines;
    }

    // Collect all of the dartdoc line comments.
    while (line >= 0) {
      final String text = StringUtil.trimLeading(getLine(document, line));

      if (text.startsWith("///")) {
        lines.add(0, text);
        line--;

        continue;
      }

      // anything else means we end the dartdoc comment
      break;
    }

    return lines;
  }

  private static String getLine(Document document, int line) {
    return document.getText(new TextRange(document.getLineStartOffset(line - 1), document.getLineEndOffset(line - 1)));
  }
}
