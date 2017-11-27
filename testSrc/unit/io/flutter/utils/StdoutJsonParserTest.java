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
    parser.flush();

    assertArrayEquals(
      "validating parser results",
      new String[]{"hello\n", "there\n", "[{'foo':'bar'}]\n", "bye\n"},
      parser.getAvailableLines().toArray()
    );
  }

  @Test
  public void flush() throws Exception {
    StdoutJsonParser parser = new StdoutJsonParser();
    parser.appendOutput("hello\n");
    parser.appendOutput("there");
    parser.flush();

    assertArrayEquals(
      "validating parser results",
      new String[]{"hello\n", "there"},
      parser.getAvailableLines().toArray()
    );

    parser = new StdoutJsonParser();
    parser.appendOutput("hello\n");
    parser.appendOutput("there");

    assertArrayEquals(
      "validating parser results",
      new String[]{"hello\n"},
      parser.getAvailableLines().toArray()
    );
  }

  @Test
  public void split_json() throws Exception {
    final StdoutJsonParser parser = new StdoutJsonParser();
    parser.appendOutput("hello\n");
    parser.appendOutput("there\n");
    parser.appendOutput("[{'foo':");
    parser.appendOutput("'bar'}]\n");
    parser.appendOutput("bye\n");
    parser.flush();

    assertArrayEquals(
      "validating parser results",
      new String[]{"hello\n", "there\n", "[{'foo':'bar'}]\n", "bye\n"},
      parser.getAvailableLines().toArray()
    );
  }
}
