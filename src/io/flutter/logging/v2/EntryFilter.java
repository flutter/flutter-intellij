/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging.v2;

import io.flutter.logging.FlutterLogEntry;
import io.flutter.logging.FlutterLogTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class EntryFilter {
  @Nullable
  private final String text;
  private final boolean isRegex;
  private final boolean isMatchCase;

  public EntryFilter(@Nullable String text) {
    this(text, false, false);
  }

  public EntryFilter(@Nullable String text, boolean isMatchCase, boolean isRegex) {
    this.text = text;
    this.isMatchCase = isMatchCase;
    this.isRegex = isRegex;
  }

  @Nullable
  public String getText() {
    return text;
  }

  public boolean accept(@NotNull FlutterLogEntry entry) {
    if (text == null) {
      return true;
    }
    final String standardText = isMatchCase ? text : text.toLowerCase();
    final String standardMessage = isMatchCase ? entry.getMessage() : entry.getMessage().toLowerCase();
    final String standardCategory = isMatchCase ? entry.getCategory() : entry.getCategory().toLowerCase();
    if (acceptByCheckingRegexOption(standardCategory, standardText)) {
      return true;
    }
    return acceptByCheckingRegexOption(standardMessage, standardText);
  }

  private boolean acceptByCheckingRegexOption(@NotNull String message, @NotNull String text) {
    if (isRegex) {
      return message.matches("(?s).*" + text + ".*");
    }
    return message.contains(text);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final EntryFilter filter = (EntryFilter)o;
    return isRegex == filter.isRegex &&
           isMatchCase == filter.isMatchCase &&
           Objects.equals(text, filter.text);
  }

  @Override
  public int hashCode() {
    return Objects.hash(text, isRegex, isMatchCase);
  }
}
