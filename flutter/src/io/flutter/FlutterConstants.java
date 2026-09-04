/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class FlutterConstants {

  // From: https://github.com/dart-lang/sdk/blob/main/pkg/_fe_analyzer_shared/lib/src/scanner/token.dart
  public static final Set<String> DART_KEYWORDS = Set.of(
    "abstract",
    "as",
    "assert",
    "async",
    "await",
    "base",
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
    "extension",
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
    "interface",
    "is",
    "late",
    "library",
    "mixin",
    "native",
    "new",
    "null",
    "of",
    "on",
    "operator",
    "part",
    "patch",
    "required",
    "rethrow",
    "return",
    "sealed",
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
    "when",
    "while",
    "with",
    "yield"
  );

  // From: https://github.com/flutter/flutter/blob/main/packages/flutter_tools/lib/src/commands/create_base.dart
  @NotNull
  public final static Set<String> FLUTTER_PACKAGE_DEPENDENCIES = Set.of(
    "analyzer",
    "args",
    "async",
    "collection",
    "convert",
    "crypto",
    "flutter",
    "flutter_test",
    "front_end",
    "html",
    "http",
    "intl",
    "io",
    "isolate",
    "kernel",
    "logging",
    "matcher",
    "meta",
    "mime",
    "path",
    "plugin",
    "pool",
    "test",
    "utf",
    "watcher",
    "yaml"
  );

  // Aligned w/ VSCode (https://github.com/flutter/flutter-intellij/issues/2682)
  public static final String RELOAD_REASON_MANUAL = "manual";
  public static final String RELOAD_REASON_SAVE = "save";
  public static final String RELOAD_REASON_TOOL = "tool";

  public static final String FLUTTER_SETTINGS_PAGE_ID = "flutter.settings";
  public static final String INDEPENDENT_PATH_SEPARATOR = "/";

  public static final String URL_GETTING_STARTED = FlutterBundle.message("flutter.io.gettingStarted.url");
  public static final String URL_GETTING_STARTED_IDE = FlutterBundle.message("flutter.io.gettingStarted.IDE.url");
  public static final String URL_RUN_AND_DEBUG = FlutterBundle.message("flutter.io.runAndDebug.url");

  private FlutterConstants() {

  }
}
