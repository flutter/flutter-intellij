/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.samples;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class FlutterSampleTest {

  static String parseShort(String description) {
    return FlutterSample.parseShortHtmlDescription(description);
  }

  static String parse(String description) {
    return FlutterSample.parseHtmlDescription(description);
  }

  @Test
  public void testParseShortHtmlDescription() {
    assertEquals("<p>One line.</p>", parseShort("One line. And another."));
    assertEquals("<p>One line.</p>", parseShort("One line.\nAnd another."));
  }

  @Test
  public void testParseHtmlDescription() {
    assertEquals("<p>One line. And another.</p>", parse("One line. And another."));
  }

  @Test
  public void testParseDocLink() {
    assertEquals("<p><strong>Card</strong></p>", parse("[Card]"));
  }

  @Test
  public void testParseEscapes() {
    assertEquals("<p>[Card]</p>", parse("\\[Card\\]"));
  }

  static final String CARD_DESC = "This sample shows creation of a [Card] widget that shows album information\nand two actions.";

  @Test
  public void testParseCardDescription() {
    assertEquals("<p>This sample shows creation of a <strong>Card</strong> widget that shows album information and two actions.</p>",
                 parse(CARD_DESC));
  }

  static final String SCAFFOLD_DESC =
    "This example shows a [Scaffold] with an [AppBar], a [BottomAppBar] and a\n[FloatingActionButton]. The [body] is a [Text] placed in a [Center] in order\nto center the text within the [Scaffold] and the [FloatingActionButton] is\ncentered and docked within the [BottomAppBar] using\n[FloatingActionButtonLocation.centerDocked]. The [FloatingActionButton] is\nconnected to a callback that increments a counter.";

  @Test
  public void testScaffoldDescription() {
    assertEquals(
      "<p>This example shows a <strong>Scaffold</strong> with an <strong>AppBar</strong>, a <strong>BottomAppBar</strong> and a <strong>FloatingActionButton</strong>. The <strong>body</strong> is a <strong>Text</strong> placed in a <strong>Center</strong> in order to center the text within the <strong>Scaffold</strong> and the <strong>FloatingActionButton</strong> is centered and docked within the <strong>BottomAppBar</strong> using <strong>FloatingActionButtonLocation.centerDocked</strong>. The <strong>FloatingActionButton</strong> is connected to a callback that increments a counter.</p>",
      parse(SCAFFOLD_DESC));
  }
}
