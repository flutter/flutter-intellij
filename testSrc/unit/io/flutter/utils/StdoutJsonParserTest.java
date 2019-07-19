/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class StdoutJsonParserTest {
  @Test
  public void simple() {
    final StdoutJsonParser parser = new StdoutJsonParser();
    parser.appendOutput("hello\n");
    parser.appendOutput("there\n");
    parser.appendOutput("[{'foo':'bar'}]\n");
    parser.appendOutput("bye\n");

    assertArrayEquals(
      "validating parser results",
      new String[]{"hello\n", "there\n", "[{'foo':'bar'}]\n", "bye\n"},
      parser.getAvailableLines().toArray()
    );
  }

  @Test
  public void appendsWithoutLineBreaks() {
    StdoutJsonParser parser = new StdoutJsonParser();
    parser.appendOutput("hello\nnow\n");
    parser.appendOutput("there");
    parser.appendOutput("world");

    assertArrayEquals(
      "validating parser results",
      new String[]{"hello\n", "now\n", "there", "world"},
      parser.getAvailableLines().toArray()
    );

    parser = new StdoutJsonParser();
    parser.appendOutput("hello\n");
    parser.appendOutput("there");

    assertArrayEquals(
      "validating parser results",
      new String[]{"hello\n", "there"},
      parser.getAvailableLines().toArray()
    );
  }

  @Test
  public void splitJson() {
    final StdoutJsonParser parser = new StdoutJsonParser();
    parser.appendOutput("hello\n");
    parser.appendOutput("there\n");
    parser.appendOutput("[{'foo':");
    parser.appendOutput("'bar'}]\n");
    parser.appendOutput("bye\n");

    assertArrayEquals(
      "validating parser results",
      new String[]{"hello\n", "there\n", "[{'foo':'bar'}]\n", "bye\n"},
      parser.getAvailableLines().toArray()
    );
  }

  @Test
  public void deepNestedJson() {
    final StdoutJsonParser parser = new StdoutJsonParser();
    parser.appendOutput("hello\n");
    parser.appendOutput("there\n");
    parser.appendOutput("[{'foo':");
    parser.appendOutput("[{'bar':'baz'}]}]\n");
    parser.appendOutput("bye\n");

    assertArrayEquals(
      "validating parser results",
      new String[]{"hello\n", "there\n", "[{'foo':[{'bar':'baz'}]}]\n", "bye\n"},
      parser.getAvailableLines().toArray()
    );
  }

  @Test
  public void unterminatedJson() {
    final StdoutJsonParser parser = new StdoutJsonParser();
    parser.appendOutput("hello\n");
    parser.appendOutput("there\n");
    parser.appendOutput("[{'bar':'baz'");
    // The JSON has not yet terminated with a '}]' sequence.

    assertArrayEquals(
      "validating parser results",
      new String[]{"hello\n", "there\n"},
      parser.getAvailableLines().toArray()
    );
  }

  @Test
  public void outputConcatenatedJson() {
    final StdoutJsonParser parser = new StdoutJsonParser();
    parser.appendOutput(
      "[{\"event\":\"app.progress\",\"params\":{\"appId\":\"363879f2-74d7-46e5-bfa9-1654a8c69923\",\"id\":\"12\",\"progressId\":\"hot.restart\",\"message\":\"Performing hot restart...\"}}]Performing hot restart…");
    assertArrayEquals(
      "validating parser results",
      new String[]{
        "[{\"event\":\"app.progress\",\"params\":{\"appId\":\"363879f2-74d7-46e5-bfa9-1654a8c69923\",\"id\":\"12\",\"progressId\":\"hot.restart\",\"message\":\"Performing hot restart...\"}}]",
        "Performing hot restart…"},
      parser.getAvailableLines().toArray()
    );
  }
}
