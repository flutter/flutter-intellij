/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.google.common.annotations.VisibleForTesting;
import io.flutter.inspector.DiagnosticLevel;
import io.flutter.inspector.DiagnosticsNode;

import java.util.regex.Pattern;

public class FlutterErrorHelper {
  private static final Pattern numberPattern = Pattern.compile("[0-9]+");

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

    // remove some prefixes
    if (normalized.startsWith("a ")) {
      normalized = normalized.substring("a ".length());
    }
    else if (normalized.startsWith("the ")) {
      normalized = normalized.substring("the ".length());
    }

    // remove some suffixes
    if (normalized.endsWith(".")) {
      normalized = normalized.substring(0, normalized.length() - ".".length());
    }

    // remove '()' from method names
    normalized = normalized.replaceAll("\\(\\)", "");

    // replace numbers with a string constant
    normalized = numberPattern.matcher(normalized).replaceAll("xxx");

    // convert spaces to dashes
    normalized = normalized.trim().replaceAll(" ", "-");

    // "renderflex-overflowed-by-xxx-pixels-on-the-right"
    // "no-material-widget-found"
    // "scaffold.of-called-with-a-context-that-does-not-contain-a-scaffold"
    return normalized;
  }
}
