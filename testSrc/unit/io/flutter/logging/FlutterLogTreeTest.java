/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FlutterLogTreeTest {

  @Test
  public void testMustAcceptIfFilterIsNull() {
    final FlutterLogTree.ContainsTextFilter filterNormal = new FlutterLogTree.ContainsTextFilter(null);
    final FlutterLogTree.ContainsTextFilter filterRegex = new FlutterLogTree.ContainsTextFilter(null, false, true);
    final FlutterLogEntry entry = new FlutterLogEntry(0, "", "");
    assertTrue(filterNormal.accept(entry));
    assertTrue(filterRegex.accept(entry));
  }

  @Test
  public void testAcceptInRegex() {
    final FlutterLogTree.ContainsTextFilter filterRegex = new FlutterLogTree.ContainsTextFilter(".*hello.*", false, true);
    final FlutterLogEntry entryMatchWithRegexByMessage = new FlutterLogEntry(0, "random", "log hello message");
    final FlutterLogEntry entryMatchWithRegexByMessageWithBreakLine = new FlutterLogEntry(0, "random", "log hello message\nnewline\n");
    final FlutterLogEntry entryMatchWithRegexByCategory = new FlutterLogEntry(0, "hello", "log random message");
    final FlutterLogEntry entryNotMatchWithRegex = new FlutterLogEntry(0, "random", "log random message");
    assertTrue(filterRegex.accept(entryMatchWithRegexByMessage));
    assertTrue(filterRegex.accept(entryMatchWithRegexByMessageWithBreakLine));
    assertTrue(filterRegex.accept(entryMatchWithRegexByCategory));
    assertFalse(filterRegex.accept(entryNotMatchWithRegex));
  }

  @Test
  public void testAcceptInNormalMode() {
    final FlutterLogTree.ContainsTextFilter filterRegex = new FlutterLogTree.ContainsTextFilter("hello", false, true);
    final FlutterLogEntry entryMatchByMessage = new FlutterLogEntry(0, "random", "log hello message");
    final FlutterLogEntry entryMatchByCategory = new FlutterLogEntry(0, "hello", "log random message");
    final FlutterLogEntry entryNotMatch = new FlutterLogEntry(0, "random", "log random message");

    assertTrue(filterRegex.accept(entryMatchByMessage));
    assertTrue(filterRegex.accept(entryMatchByCategory));
    assertFalse(filterRegex.accept(entryNotMatch));
  }

  @Test
  public void testMustAcceptInMatchCaseMode() {
    final FlutterLogEntry entry = new FlutterLogEntry(0, "random", "log hello message");
    final FlutterLogTree.ContainsTextFilter filterMatchCaseNormal = new FlutterLogTree.ContainsTextFilter("hello", true, false);
    final FlutterLogTree.ContainsTextFilter filterMatchCaseRegex = new FlutterLogTree.ContainsTextFilter("hello", true, true);
    final FlutterLogTree.ContainsTextFilter filterInvalidMatchCaseNormal = new FlutterLogTree.ContainsTextFilter("Hello", true, false);
    final FlutterLogTree.ContainsTextFilter filterInvalidMatchCaseRegex = new FlutterLogTree.ContainsTextFilter("Hello", true, true);
    assertTrue(filterMatchCaseNormal.accept(entry));
    assertTrue(filterMatchCaseRegex.accept(entry));
    assertFalse(filterInvalidMatchCaseNormal.accept(entry));
    assertFalse(filterInvalidMatchCaseRegex.accept(entry));
  }
}