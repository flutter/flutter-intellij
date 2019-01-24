/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging.text;

import com.intellij.execution.filters.BrowserHyperlinkInfo;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static io.flutter.logging.text.StyledTextTestUtils.*;

public class LineParserTest {
  static final SimpleTextAttributes PLAIN_RED = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, Color.RED);
  static final SimpleTextAttributes BOLD_RED = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color.RED);
  static final SimpleTextAttributes STRIKEOUT_RED = new SimpleTextAttributes(SimpleTextAttributes.STYLE_STRIKEOUT, Color.RED);
  static final SimpleTextAttributes BOLD_BLUE = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, Color.BLUE);
  static final SimpleTextAttributes LINK = SimpleTextAttributes.LINK_ATTRIBUTES;

  static class TestParser extends LineParser {
    final List<StyledText> parsed = new ArrayList<>();

    TestParser() {
      super(new UrlFilter());
    }

    @Override
    public void write(@NotNull StyledText styledText) {
      parsed.add(styledText);
    }
  }

  @Test
  public void test8bitBasic() {
    expectParsesTo("Hello", new StyledText("Hello", null));
    expectParsesTo("\u001b[31mHello", new StyledText("Hello", PLAIN_RED));
    expectParsesTo("\u001b[31mHello\u001b[0m World",
                   new StyledText("Hello", PLAIN_RED), new StyledText(" World", null));
  }

  @Test
  public void test8bitFontStyle() {
    expectParsesTo("\u001b[31;1mHello", text("Hello", BOLD_RED));
    expectParsesTo("Hello \u001b[31;1mWorld\u001b[0m", text("Hello ", null), text("World", BOLD_RED));
    expectParsesTo("\u001b[34;1mHello", text("Hello", BOLD_BLUE));
    expectParsesTo("\u001b[31;1mHello\u001b[0m World", text("Hello", BOLD_RED), text(" World", null));
    expectParsesTo("\u001b[9;31mHello", text("Hello", STRIKEOUT_RED));
    expectParsesTo("\u001b[31;1mHello\u001b[0m\u001b[34;1m World\u001b[0m",
                   text("Hello", BOLD_RED), text(" World", BOLD_BLUE));
  }

  @Test
  public void testLinks() {
    expectParsesTo("https://www.dartlang.org", text("https://www.dartlang.org", LINK));
    expectParsesTo("Hello https://www.dartlang.org", text("Hello ", null), text("https://www.dartlang.org", LINK));
    expectParsesTo("Hello https://www.dartlang.org from test",
                   text("Hello ", null),
                   text("https://www.dartlang.org", LINK),
                   text(" from test", null));
    expectParsesTo("Hello https://www.dartlang.org from \u001b[31;1mtest\u001b[0m",
                   text("Hello ", null),
                   text("https://www.dartlang.org", LINK),
                   text(" from ", null),
                   text("test", BOLD_RED));
  }

  @Test
  public void testLinkTag() {
    expectParsesTo("https://www.dartlang.org", new StyledText("https://www.dartlang.org", LINK,
                                                              new BrowserHyperlinkInfo("https://www.dartlang.org")));
  }

  @Test
  public void testParse() {
    final TestParser parser = new TestParser();
    parser.parse("\u001b[31;1mHello");
    // Validate styles stick.
    assertEquals(BOLD_RED, parser.style);
    assertEquals(arrayOf(text("Hello", BOLD_RED)), parser.parsed);

    parser.parse(" World");
    assertEquals(BOLD_RED, parser.style);
    assertEquals(arrayOf(text("Hello", BOLD_RED), text(" World", BOLD_RED)), parser.parsed);

    // Reset.
    parser.parse("\u001b[0m");
    assertEquals(null, parser.style);

    parser.parse("!");
    assertEquals(arrayOf(text("Hello", BOLD_RED), text(" World", BOLD_RED), text("!", null)), parser.parsed);
  }

  private static void expectParsesTo(String str, StyledText... results) {
    final TestParser parser = new TestParser();
    parser.parse(str);
    assertEquals(results, parser.parsed);
  }
}
