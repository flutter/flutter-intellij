/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging.text;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public abstract class LineParser {

  private final StringBuilder buffer = new StringBuilder();
  protected final List<Filter> filters;
  @VisibleForTesting
  protected SimpleTextAttributes style;
  private String str;
  private int index;
  private int length;
  private boolean flush;
  private boolean reset;
  private SimpleTextAttributes previousStyle;
  private boolean flushPrevious;

  public LineParser(Filter... filter) {
    this(Arrays.asList(filter));
  }

  public LineParser(List<Filter> filters) {
    this.filters = filters;
  }

  private static Object toStyle(String str) {
    switch (str) {
      case "1":
        return SimpleTextAttributes.STYLE_BOLD;
      case "3":
        return SimpleTextAttributes.STYLE_ITALIC;
      case "4":
        return SimpleTextAttributes.STYLE_UNDERLINE;
      case "9":
        return SimpleTextAttributes.STYLE_STRIKEOUT;
      case "30":
        return JBColor.BLACK;
      case "31":
        return JBColor.RED;
      case "32":
        return JBColor.GREEN;
      case "33":
        return JBColor.YELLOW;
      case "34":
        return JBColor.BLUE;
      case "35":
        return JBColor.MAGENTA;
      case "36":
        return JBColor.CYAN;
      case "37":
        return JBColor.WHITE;
      case "38":
        return JBColor.GRAY;
      default:
        return null;
    }
  }

  protected void write(@NotNull String string, @Nullable SimpleTextAttributes style) {
    write(string, style, null);
  }

  protected void write(@NotNull String string, @Nullable SimpleTextAttributes style, @Nullable Object tag) {
    write(new StyledText(string, style, tag));
  }

  public abstract void write(@NotNull StyledText styledText);

  public void parse(@NotNull String str) {
    if (str.isEmpty()) {
      return;
    }

    final List<Filter.ResultItem> resultItems = new ArrayList<>();
    for (Filter filter : filters) {
      final Filter.Result result = filter.applyFilter(str, str.length());
      if (result == null) {
        continue;
      }
      resultItems.addAll(result.getResultItems());
    }
    resultItems.sort(Comparator.comparingInt(Filter.ResultItem::getHighlightStartOffset));

    int cursor = 0;
    for (Filter.ResultItem item : resultItems) {
      final HyperlinkInfo hyperlinkInfo = item.getHyperlinkInfo();
      if (hyperlinkInfo != null) {
        final int start = item.getHighlightStartOffset();
        final int end = item.getHighlightEndOffset();
        // Leading text.
        if (cursor < start) {
          parseChunk(str.substring(cursor, start));
        }
        write(str.substring(start, end), SimpleTextAttributes.LINK_ATTRIBUTES, hyperlinkInfo);
        cursor = end;
      }
    }

    // Trailing text
    if (cursor < str.length()) {
      parseChunk(str.substring(cursor));
    }
  }

  private void parseChunk(@NotNull String str) {
    this.str = str;
    length = str.length();
    index = 0;
    while (hasNext()) {
      parseNext();
    }
  }

  private void parseNext() {
    while (hasNext()) {
      final char next = advance();
      if (next == '\u001b') {
        final boolean readEscape = readEscape();
        if (!readEscape) {
          append(next);
        }
        else {
          flush();
        }
      }
      else {
        append(next);
      }
    }

    flush = true;
    flush();
  }

  private boolean readEscape() {
    final boolean reset = readReset();
    if (reset) {
      this.reset = true;
      return true;
    }
    else {
      final SimpleTextAttributes style = readStyle();
      if (style != null) {
        flushPrevious = true;
        previousStyle = this.style;
        this.style = style;
        flush = true;
        return true;
      }
    }
    return false;
  }

  private void flush() {
    if (flush || reset) {
      if (buffer.length() > 0) {
        write(buffer.toString(), flushPrevious ? previousStyle : style);
        buffer.setLength(0);
      }
      flush = false;
      flushPrevious = false;
    }
    if (reset) {
      style = null;
      reset = false;
    }
  }

  private void append(char ch) {
    flush();
    buffer.append(ch);
  }

  private boolean readReset() {
    if (peek(3).equals("[0m")) {
      eat(3);
      return true;
    }
    return false;
  }

  @Nullable
  private SimpleTextAttributes readStyle() {
    if (!peek(1).equals("[")) {
      return null;
    }

    final String remainder = str.substring(index);
    final int escapeEndIndex = remainder.indexOf("m");
    if (escapeEndIndex == -1) {
      return null;
    }

    final String paramString = remainder.substring(1, escapeEndIndex);

    // Eat params + leading [ and trailing m
    eat(paramString.length() + 2);

    Color color = null;
    int fontStyle = SimpleTextAttributes.STYLE_PLAIN;

    for (String param : paramString.split(";")) {
      final Object style = toStyle(param);
      if (style instanceof Color) {
        color = (Color)style;
      }
      else if (style instanceof Integer) {
        fontStyle |= (int)style;
      }
    }

    //noinspection MagicConstant
    return new SimpleTextAttributes(fontStyle, color);
  }

  private String peek(int n) {
    return index + n > length ? "" : str.substring(index, index + n);
  }

  private void eat(int n) {
    index += n;
  }

  private char advance() {
    return str.charAt(index++);
  }

  boolean hasNext() {
    return index < str.length();
  }

  boolean atEnd() {
    return index >= str.length();
  }

  public void clear() {
    this.style = null;
  }
}
