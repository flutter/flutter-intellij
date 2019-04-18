/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.samples;

import com.google.common.annotations.VisibleForTesting;
import com.petebevin.markdown.MarkdownProcessor;
import org.jetbrains.annotations.NotNull;

public class FlutterSample {
  @NotNull
  private final String element;
  @NotNull
  private final String library;
  @NotNull
  private final String id;
  @NotNull
  private final String file;
  @NotNull
  private final String sourcePath;
  @NotNull
  private final String description;

  FlutterSample(@NotNull String element, @NotNull String library, @NotNull String id, @NotNull String file,
                @NotNull String sourcePath, @NotNull String description) {
    this.element = element;
    this.library = library;
    this.id = id;
    this.file = file;
    this.sourcePath = sourcePath;
    this.description = description;
  }

  @Override
  public String toString() {
    return getDisplayLabel();
  }

  /**
   * Get a label suitable for display in a chooser (e.g., combobox).
   */
  public String getDisplayLabel() {
    // TODO(pq): come up with disambiguated labels.
    // TODO(pq): consider adding (package suffix too).
    // Index isn't quite enough for disambiguation (there are still dups like DeletableChips).
    //final String[] parts = id.split("\\.");
    //final String lastPart = parts[parts.length-1];
    //
    //String suffix = "";
    //try {
    //  final int index = Integer.parseInt(lastPart);
    //  if (index != 1) {
    //    suffix = " (" + index + ")";
    //  }
    //} catch (NumberFormatException e) {
    //  // ignore
    //}
    //
    //return getElement() + suffix;
    return getElement();
  }

  public String getShortHtmlDescription() {
    return "<html>" + parseShortHtmlDescription(description) + "</html>";
  }

  @VisibleForTesting
  public static String parseShortHtmlDescription(@NotNull String description) {
    // Trim to first sentence.
    int sentenceBreakIndex = description.indexOf(". ");
    if (sentenceBreakIndex == -1) {
      sentenceBreakIndex = description.indexOf(".\n");
    }
    if (sentenceBreakIndex != -1) {
      description = description.substring(0, sentenceBreakIndex + 1);
    }

    return parseHtmlDescription(description);
  }

  @VisibleForTesting
  public static String parseHtmlDescription(@NotNull String description) {
    // Remove newlines.
    description = description.replace('\n', ' ');

    // Remove links: [Card] => **Card**
    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < description.length(); ++i) {
      final char c = description.charAt(i);
      if ((c == '[' || c == ']') && (i == 0 || description.charAt(i - 1) != '\\')) {
        builder.append("**");
      }
      else {
        builder.append(c);
      }
    }

    return new MarkdownProcessor().markdown(builder.toString()).trim();
  }

  @NotNull
  public String getLibrary() {
    return library;
  }

  @NotNull
  public String getId() {
    return id;
  }

  @NotNull
  public String getElement() {
    return element;
  }

  @NotNull
  public String getDescription() {
    return description;
  }

  @NotNull
  public String getFile() {
    return file;
  }

  @NotNull
  public String getSourcePath() {
    return sourcePath;
  }
}
