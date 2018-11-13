/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter;

import java.util.Arrays;
import java.util.List;

public class FlutterConstants {

  // From: analyzer Token.Keywords
  public static final List<String> DART_KEYWORDS = Arrays.asList(
    "abstract",
    "as",
    "assert",
    "async",
    "await",
    "break",
    "case",
    "catch",
    "class",
    "const",
    "continue",
    "covariant",
    "default",
    "deferred",
    "do",
    "dynamic",
    "else",
    "enum",
    "export",
    "extends",
    "external",
    "factory",
    "false",
    "final",
    "finally",
    "for",
    "function",
    "get",
    "hide",
    "if",
    "implements",
    "import",
    "in",
    "is",
    "library",
    "native",
    "new",
    "null",
    "of",
    "on",
    "operator",
    "part",
    "patch",
    "rethrow",
    "return",
    "set",
    "show",
    "source",
    "static",
    "super",
    "switch",
    "sync",
    "this",
    "throw",
    "true",
    "try",
    "typedef",
    "var",
    "void",
    "while",
    "with",
    "yield"
  );

  // From: https://github.com/flutter/flutter/blob/master/packages/flutter_tools/lib/src/commands/create.dart
  public final static List<String> FLUTTER_PACKAGE_DEPENDENCIES = Arrays.asList(
    "args",
    "async",
    "collection",
    "convert",
    "flutter",
    "flutter_test",
    "html",
    "intl",
    "logging",
    "matcher",
    "mime",
    "path",
    "plugin",
    "pool",
    "test",
    "utf",
    "watcher",
    "yaml");

  // Aligned w/ VSCode (https://github.com/flutter/flutter-intellij/issues/2682)
  public static String RELOAD_REASON_SAVE = "save";
  public static String RELOAD_REASON_MANUAL = "manual";

  public static final String FLUTTER_SETTINGS_PAGE_ID = "flutter.settings";
  public static final String INDEPENDENT_PATH_SEPARATOR = "/";
  public static final int MAX_MODULE_NAME_LENGTH = 30;

  private FlutterConstants() {

  }
}
