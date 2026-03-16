/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for DiagnosticsNode JSON parsing, specifically the style and level member parsing
 * which must tolerate unknown enum values sent by newer Flutter framework versions.
 *
 * Regression test for https://github.com/flutter/flutter-intellij/issues/8839:
 * Windows desktop debug console shows no errors because newer Flutter SDKs can send
 * style values not present in the plugin's DiagnosticsTreeStyle enum, causing
 * IllegalArgumentException that silently swallows the entire error event.
 */
public class DiagnosticsNodeTest {

  // --- getStyle() --- //

  /**
   * The core regression test for issue #8839.
   *
   * Before the fix: DiagnosticsTreeStyle.valueOf("unknownFutureStyleValue") throws
   * IllegalArgumentException, which propagates out of processFlutterErrorEvent() and
   * is silently caught in the FlutterConsoleLogManager queue, showing nothing to the user.
   *
   * After the fix: returns the default value (sparse) and rendering continues normally.
   */
  @Test
  public void getStyle_returnsDefaultForUnknownValue() {
    final JsonObject json = new JsonObject();
    json.addProperty("style", "unknownFutureStyleValue");
    json.addProperty("description", "Exception thrown building MyWidget");

    final DiagnosticsNode node = new DiagnosticsNode(json, null, false, null);

    // Before fix: throws IllegalArgumentException
    // After fix:  returns the default (sparse)
    assertEquals(DiagnosticsTreeStyle.sparse, node.getStyle());
  }

  @Test
  public void getStyle_returnsDefaultWhenFieldAbsent() {
    final JsonObject json = new JsonObject();
    json.addProperty("description", "some node");

    final DiagnosticsNode node = new DiagnosticsNode(json, null, false, null);
    assertEquals(DiagnosticsTreeStyle.sparse, node.getStyle());
  }

  @Test
  public void getStyle_returnsDefaultWhenFieldIsNull() {
    final JsonObject json = new JsonObject();
    json.add("style", JsonNull.INSTANCE);
    json.addProperty("description", "some node");

    final DiagnosticsNode node = new DiagnosticsNode(json, null, false, null);
    assertEquals(DiagnosticsTreeStyle.sparse, node.getStyle());
  }

  @Test
  public void getStyle_parsesAllKnownEnumValues() {
    for (final DiagnosticsTreeStyle style : DiagnosticsTreeStyle.values()) {
      final JsonObject json = new JsonObject();
      json.addProperty("style", style.name());
      final DiagnosticsNode node = new DiagnosticsNode(json, null, false, null);
      assertEquals("Expected " + style.name() + " to round-trip", style, node.getStyle());
    }
  }

  // --- getLevel() --- //
  // getLevelMember() already has the try/catch fix — verify it stays correct.

  @Test
  public void getLevel_returnsDefaultForUnknownValue() {
    final JsonObject json = new JsonObject();
    json.addProperty("level", "unknownFutureLevelValue");

    final DiagnosticsNode node = new DiagnosticsNode(json, null, false, null);
    assertEquals(DiagnosticLevel.info, node.getLevel());
  }

  @Test
  public void getLevel_parsesAllKnownEnumValues() {
    for (final DiagnosticLevel level : DiagnosticLevel.values()) {
      final JsonObject json = new JsonObject();
      json.addProperty("level", level.name());
      final DiagnosticsNode node = new DiagnosticsNode(json, null, false, null);
      assertEquals("Expected " + level.name() + " to round-trip", level, node.getLevel());
    }
  }
}
