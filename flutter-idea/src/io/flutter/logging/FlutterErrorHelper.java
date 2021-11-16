/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.util.text.StringUtil;
import io.flutter.inspector.DiagnosticLevel;
import io.flutter.inspector.DiagnosticsNode;

import java.util.regex.Pattern;

public class FlutterErrorHelper {
  private static final Pattern numberPattern = Pattern.compile("[0-9]+(\\.[0-9]+)?");

  public static String getAnalyticsId(DiagnosticsNode node) {
    for (DiagnosticsNode property : node.getInlineProperties()) {
      if (property.getLevel() == DiagnosticLevel.summary) {
        final String description = property.getDescription();
        return getAnalyticsId(description);
      }
    }

    return null;
  }

  @VisibleForTesting
  public static String getAnalyticsId(String errorSummary) {
    // "A RenderFlex overflowed by 1183 pixels on the right."

    // TODO(devoncarew): Re-evaluate this normalization after we've implemented a kernel transformer
    // for Flutter error description objects.

    // normalize to lower case
    String normalized = errorSummary.toLowerCase().trim();

    // Ensure that asserts are broken across lines.
    normalized = normalized.replaceAll(": failed assertion:", ":\nfailed assertion:");

    // If it's an assertion, remove the leading assertion path.
    final String[] lines = normalized.split("\n");
    if (lines.length >= 2 && lines[0].endsWith(".dart':") && lines[1].startsWith("failed assertion:")) {
      normalized = StringUtil.join(lines, 1, lines.length, "\n");
      normalized = normalized.trim();
    }

    // Take the first sentence.
    if (normalized.contains(". ")) {
      normalized = normalized.substring(0, normalized.indexOf(". "));
      normalized = normalized.trim();
    }

    // Take the first line.
    if (normalized.contains("\n")) {
      normalized = normalized.substring(0, normalized.indexOf("\n"));
    }

    // Take text before the first colon.
    if (normalized.contains(":")) {
      normalized = normalized.substring(0, normalized.indexOf(":"));
    }

    // remove some suffixes
    if (normalized.endsWith(".")) {
      normalized = normalized.substring(0, normalized.length() - ".".length());
    }

    // remove content in parens
    normalized = normalized.replaceAll("\\([^)]*\\)", "");

    // replace numbers with a string constant
    normalized = numberPattern.matcher(normalized).replaceAll("xxx");

    // collapse multiple spaces
    normalized = normalized.replaceAll("\\s+", " ");

    normalized = normalized.trim();

    // convert spaces to dashes
    normalized = normalized.trim().replaceAll(" ", "-");

    // "renderflex-overflowed-by-xxx-pixels-on-the-right"
    // "no-material-widget-found"
    // "scaffold.of-called-with-a-context-that-does-not-contain-a-scaffold"
    return normalized;
  }
}
