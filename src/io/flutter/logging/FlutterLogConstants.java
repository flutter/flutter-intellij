/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import static com.intellij.execution.ui.ConsoleViewContentType.registerNewConsoleViewType;

public final class FlutterLogConstants {
  private FlutterLogConstants() {
  }

  @NotNull
  public static final TextAttributesKey NONE_OUTPUT_KEY =
    TextAttributesKey.createTextAttributesKey("FLUTTER_LOG_NONE_OUTPUT", ConsoleViewContentType.NORMAL_OUTPUT_KEY);
  @NotNull
  public static final TextAttributesKey FINEST_OUTPUT_KEY =
    TextAttributesKey.createTextAttributesKey("FLUTTER_LOG_FINEST_OUTPUT", ConsoleViewContentType.NORMAL_OUTPUT_KEY);
  @NotNull
  public static final TextAttributesKey FINER_OUTPUT_KEY =
    TextAttributesKey.createTextAttributesKey("FLUTTER_LOG_FINER_OUTPUT", ConsoleViewContentType.NORMAL_OUTPUT_KEY);
  @NotNull
  public static final TextAttributesKey FINE_OUTPUT_KEY =
    TextAttributesKey.createTextAttributesKey("FLUTTER_LOG_FINE_OUTPUT", ConsoleViewContentType.NORMAL_OUTPUT_KEY);
  @NotNull
  public static final TextAttributesKey CONFIG_OUTPUT_KEY =
    TextAttributesKey.createTextAttributesKey("FLUTTER_LOG_CONFIG_OUTPUT", ConsoleViewContentType.NORMAL_OUTPUT_KEY);
  @NotNull
  public static final TextAttributesKey INFO_OUTPUT_KEY =
    TextAttributesKey.createTextAttributesKey("FLUTTER_LOG_INFO_OUTPUT", ConsoleViewContentType.NORMAL_OUTPUT_KEY);
  @NotNull
  public static final TextAttributesKey WARNING_OUTPUT_KEY =
    TextAttributesKey.createTextAttributesKey("FLUTTER_LOG_WARNING_OUTPUT", ConsoleViewContentType.SYSTEM_OUTPUT_KEY);
  @NotNull
  public static final TextAttributesKey SEVERE_OUTPUT_KEY =
    TextAttributesKey.createTextAttributesKey("FLUTTER_LOG_SEVERE_OUTPUT", ConsoleViewContentType.ERROR_OUTPUT_KEY);
  @NotNull
  public static final TextAttributesKey SHOUT_OUTPUT_KEY =
    TextAttributesKey.createTextAttributesKey("FLUTTER_LOG_SHOUT_OUTPUT", ConsoleViewContentType.ERROR_OUTPUT_KEY);

  @NotNull
  public static final Key NONE = new Key("none.level.title");
  @NotNull
  public static final Key FINEST = new Key("finest.level.title");
  @NotNull
  public static final Key FINER = new Key("finer.level.title");
  @NotNull
  public static final Key FINE = new Key("fine.level.title");
  @NotNull
  public static final Key CONFIG = new Key("config.level.title");
  @NotNull
  public static final Key INFO = new Key("info.level.title");
  @NotNull
  public static final Key WARNING = new Key("warning.level.title");
  @NotNull
  public static final Key SEVERE = new Key("severe.level.title");
  @NotNull
  public static final Key SHOUT = new Key("shout.level.title");

  static {
    registerNewConsoleViewType(NONE, new ConsoleViewContentType(NONE.toString(), NONE_OUTPUT_KEY));
    registerNewConsoleViewType(FINEST, new ConsoleViewContentType(FINEST.toString(), FINEST_OUTPUT_KEY));
    registerNewConsoleViewType(FINER, new ConsoleViewContentType(FINER.toString(), FINER_OUTPUT_KEY));
    registerNewConsoleViewType(FINE, new ConsoleViewContentType(FINE.toString(), FINE_OUTPUT_KEY));
    registerNewConsoleViewType(CONFIG, new ConsoleViewContentType(CONFIG.toString(), CONFIG_OUTPUT_KEY));
    registerNewConsoleViewType(INFO, new ConsoleViewContentType(INFO.toString(), INFO_OUTPUT_KEY));
    registerNewConsoleViewType(WARNING, new ConsoleViewContentType(WARNING.toString(), WARNING_OUTPUT_KEY));
    registerNewConsoleViewType(SEVERE, new ConsoleViewContentType(SEVERE.toString(), SEVERE_OUTPUT_KEY));
    registerNewConsoleViewType(SHOUT, new ConsoleViewContentType(SHOUT.toString(), SHOUT_OUTPUT_KEY));
  }
}
