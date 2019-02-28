/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.execution.filters.Filter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import io.flutter.logging.text.LineInfo;
import io.flutter.logging.text.StyledText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;
import java.util.List;

public class FlutterLogEntry {

  interface ContentListener extends EventListener {
    void onContentUpdate();
  }

  public enum Kind {
    RELOAD,
    RESTART,
    FLUTTER_ERROR,
    UNSPECIFIED
  }

  public static final FlutterLog.Level UNDEFINED_LEVEL = FlutterLog.Level.INFO;

  private final long timestamp;
  @NotNull
  private final String category;
  private final int level;
  @NotNull
  private String message;

  /**
   * Associated data; may be a JSON string value or DiagnosticsNode instance.
   */
  @Nullable
  private Object data;
  private int sequenceNumber = -1;

  @NotNull
  private Kind kind;
  private List<StyledText> styledText;

  @NotNull
  private final List<Filter> filters;

  /**
   * Describes any style info that was inherited from previously parsed lines.
   */
  @Nullable
  private final SimpleTextAttributes inheritedStyle;

  public FlutterLogEntry(long timestamp,
                         @NotNull String category,
                         int level,
                         @Nullable String message,
                         @NotNull Kind kind,
                         @NotNull List<StyledText> styledText,
                         @NotNull List<Filter> filters,
                         @Nullable SimpleTextAttributes inheritedStyle) {
    this.timestamp = timestamp;
    this.category = category;
    this.level = level;
    this.message = StringUtil.notNullize(message);
    this.kind = kind;
    this.styledText = styledText;
    this.filters = filters;
    this.inheritedStyle = inheritedStyle;
  }

  public FlutterLogEntry(long timestamp, @NotNull LineInfo info, int level) {
    this(timestamp, info.getCategory(), level, info.getLine(), info.getKind(), info.getStyledText(), info.getFilters(),
         info.getInheritedStyle());
  }

  @Nullable
  public Object getData() {
    return data;
  }

  public void setData(@Nullable Object data) {
    this.data = data;
  }

  @NotNull
  public Kind getKind() {
    return kind;
  }

  public void setKind(@NotNull Kind kind) {
    this.kind = kind;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public int getLevel() {
    return level;
  }

  @NotNull
  public String getLevelName() {
    return FlutterLog.Level.forValue(level).toDisplayString();
  }

  @NotNull
  public String getCategory() {
    return category;
  }

  @NotNull
  public String getMessage() {
    return message;
  }

  public void setMessage(@NotNull String message) {
    this.message = message;
    this.styledText = null;
  }

  @NotNull
  public List<Filter> getFilters() {
    return filters;
  }

  @NotNull
  public List<StyledText> getStyledText() {
    if (styledText == null) {
      styledText = calculateStyledText();
    }
    return styledText;
  }

  private List<StyledText> calculateStyledText() {
    final FlutterLogEntryParser.LineHandler lineHandler = new FlutterLogEntryParser.LineHandler(filters, inheritedStyle);
    final LineInfo lineInfo = lineHandler.parseLineInfo(getMessage(), getCategory());
    return lineInfo.getStyledText();
  }

  /**
   * Return a sequence number, or -1 if unset.
   */
  public int getSequenceNumber() {
    return sequenceNumber;
  }

  public void setSequenceNumber(int sequenceNumber) {
    this.sequenceNumber = sequenceNumber;
  }
}
