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
  public void simple() throws Exception {
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
  public void appendsWithoutLineBreaks() throws Exception {
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
  public void splitJson() throws Exception {
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
  public void deepNestedJson() throws Exception {
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
  public void unterminatedJson() throws Exception {
    final StdoutJsonParser parser = new StdoutJsonParser();
    parser.appendOutput("hello\n");
    parser.appendOutput("there\n");
    parser.appendOutput("[{'foo':");
    parser.appendOutput("[{'bar':'baz'}]");
    // The JSON has not yet terminated with a \n.

    assertArrayEquals(
      "validating parser results",
      new String[]{"hello\n", "there\n"},
      parser.getAvailableLines().toArray()
    );
  }
}
