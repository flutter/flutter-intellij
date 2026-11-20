/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.console;

import io.flutter.FlutterConstants;
import io.flutter.ide.FlutterCodeInsightFixtureTestCase;
import io.flutter.sdk.FlutterSdkUtil;

import java.util.List;

public class FlutterConsoleFoldingTest extends FlutterCodeInsightFixtureTestCase {
  private static final String FLUTTER_PATH =
    "/Users/me/flutter/bin" + FlutterConstants.INDEPENDENT_PATH_SEPARATOR + FlutterSdkUtil.flutterScriptName();

  private final FlutterConsoleFolding folding = new FlutterConsoleFolding();

  public void testFoldsCommandLineWithoutNoColor() {
    assertTrue(folding.shouldFoldLine(getProject(), FLUTTER_PATH + " pub get"));
  }

  public void testDoesNotFoldUnrelatedLine() {
    assertFalse(folding.shouldFoldLine(getProject(), "Launching lib/main.dart on Windows in debug mode..."));
  }

  public void testDoesNotFoldWhenTextPrecedesFlutterPath() {
    assertFalse(folding.shouldFoldLine(getProject(), "Running " + FLUTTER_PATH + " pub get"));
  }

  public void testPlaceholderShowsShortCommand() {
    final String line = FLUTTER_PATH + " run --machine --start-paused --device-id=windows lib/main.dart";

    assertEquals(
      "flutter run --device-id=windows lib/main.dart",
      folding.getPlaceholderText(getProject(), List.of(line)));
  }

  public void testFoldsLineThatStillHasNoColor() {
    assertTrue(folding.shouldFoldLine(getProject(), FLUTTER_PATH + " --no-color pub get"));
  }

  public void testFoldsCommandWithNoArguments() {
    assertTrue(folding.shouldFoldLine(getProject(), FLUTTER_PATH + " "));
  }

  public void testDoesNotFoldSimilarlyNamedExecutable() {
    assertFalse(folding.shouldFoldLine(getProject(), "/Users/me/flutter/bin/flutter_tools pub get"));
  }

  public void testPlaceholderWithoutMarkerIsTrimmedText() {
    assertEquals("some other line", folding.getPlaceholderText(getProject(), List.of("  some other line  ")));
  }

  //Windows tests
  private static final String WINDOWS_FLUTTER_PATH =
    "C:\\Users\\me\\flutter\\bin\\" + FlutterSdkUtil.flutterScriptName();

  public void testFoldsWindowsStyleCommandLine() {
    assertTrue(folding.shouldFoldLine(getProject(), WINDOWS_FLUTTER_PATH + " pub get"));
  }

  public void testDoesNotFoldSimilarlyNamedWindowsExecutable() {
    assertFalse(folding.shouldFoldLine(getProject(), "C:\\Users\\me\\flutter\\bin\\flutter_tools pub get"));
  }

  public void testDoesNotFoldWhenTextPrecedesWindowsPath() {
    assertFalse(folding.shouldFoldLine(getProject(), "Running " + WINDOWS_FLUTTER_PATH + " pub get"));
  }

  public void testWindowsPlaceholderShowsShortCommand() {
    final String line = WINDOWS_FLUTTER_PATH + " run --machine --start-paused --device-id=windows lib\\main.dart";

    assertEquals(
      "flutter run --device-id=windows lib\\main.dart",
      folding.getPlaceholderText(getProject(), List.of(line)));
  }

  public void testDoesNotFoldAndroidFlutterLogLine() {
    assertFalse(folding.shouldFoldLine(getProject(), "I/flutter (12345): first message"));
  }

  public void testDoesNotFoldAndroidFlutterLogLineWithPaddedPid() {
    assertFalse(folding.shouldFoldLine(getProject(), "I/flutter ( 1234): first message"));
  }

  public void testDoesNotFoldOtherAndroidLogLevels() {
    for (String level : List.of("V", "D", "I", "W", "E", "F", "A", "S")) {
      assertFalse(folding.shouldFoldLine(getProject(), level + "/flutter (12345): message"));
    }
  }

  public void testDoesNotFoldFlutterPathNotAtLineStart() {
    assertFalse(folding.shouldFoldLine(getProject(), "prefix/flutter pub get"));
  }

  public void testDoesNotFoldFullAndroidFlutterLogSequence() {
    final List<String> lines = List.of(
      "I/flutter (12345): first message",
      "I/flutter (12345): second message",
      "D/Example(12345): native message",
      "I/flutter (12345): third message");
    for (final String line : lines) {
      assertFalse(folding.shouldFoldLine(getProject(), line));
    }
  }
}
