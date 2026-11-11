/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link StringUtils#jaroWinklerSimilarity(String, String)}.
 */
public class StringUtilsTest {
  private static final double EPSILON = 1e-6;

  @Test
  public void jaroWinklerSimilarity_identicalStringsReturnOne() {
    assertEquals(1.0d, StringUtils.jaroWinklerSimilarity("", ""), EPSILON);
    assertEquals(1.0d, StringUtils.jaroWinklerSimilarity("a", "a"), EPSILON);
    assertEquals(1.0d, StringUtils.jaroWinklerSimilarity("MaterialIcons", "MaterialIcons"), EPSILON);
  }

  @Test
  public void jaroWinklerSimilarity_emptyOrDisjointStringsReturnZero() {
    assertEquals(0.0d, StringUtils.jaroWinklerSimilarity("", "MaterialIcons"), EPSILON);
    assertEquals(0.0d, StringUtils.jaroWinklerSimilarity("MaterialIcons", ""), EPSILON);
    assertEquals(0.0d, StringUtils.jaroWinklerSimilarity("a", "b"), EPSILON);
    assertEquals(0.0d, StringUtils.jaroWinklerSimilarity("abcdef", "uvwxyz"), EPSILON);
  }

  @Test
  public void jaroWinklerSimilarity_nullArgumentsThrowException() {
    // IntelliJ's @NotNull instrumentation throws IllegalArgumentException; uninstrumented javac
    // runs hit Objects.requireNonNull and throw NullPointerException.
    //noinspection DataFlowIssue
    final RuntimeException firstNull = assertThrows(
      RuntimeException.class,
      () -> StringUtils.jaroWinklerSimilarity(null, "MaterialIcons")
    );
    assertTrue(firstNull instanceof IllegalArgumentException || firstNull instanceof NullPointerException);

    //noinspection DataFlowIssue
    final RuntimeException secondNull = assertThrows(
      RuntimeException.class,
      () -> StringUtils.jaroWinklerSimilarity("MaterialIcons", null)
    );
    assertTrue(secondNull instanceof IllegalArgumentException || secondNull instanceof NullPointerException);
  }

  @Test
  public void jaroWinklerSimilarity_matchesReferenceVectors() {
    // Standard Winkler (1990) reference vectors with threshold = 0.7, prefixScale = 0.1, maxPrefix = 4.
    assertEquals(0.9611111d, StringUtils.jaroWinklerSimilarity("MARTHA", "MARHTA"), EPSILON);
    assertEquals(0.8400000d, StringUtils.jaroWinklerSimilarity("DWAYNE", "DUANE"), EPSILON);
    assertEquals(0.8133333d, StringUtils.jaroWinklerSimilarity("DIXON", "DICKSONX"), EPSILON);
    assertEquals(0.7333333d, StringUtils.jaroWinklerSimilarity("CRATE", "TRACE"), EPSILON);
  }

  @Test
  public void jaroWinklerSimilarity_prefixBoostThresholdAndCap() {
    // Jaro("ab", "abcdefg") = (2/2 + 2/7 + 2/2) / 3 = 0.7619048 >= 0.7 -> 2-char prefix boost applies.
    final double boosted = StringUtils.jaroWinklerSimilarity("ab", "abcdefg");
    final double rawJaroAbove = (1.0d + 2.0d / 7.0d + 1.0d) / 3.0d;
    assertEquals(rawJaroAbove + 0.1d * 2 * (1.0d - rawJaroAbove), boosted, EPSILON);

    // Jaro("abxxxxx", "abzzzzz") = (2/7 + 2/7 + 2/2) / 3 = 0.5238095 < 0.7 -> prefix boost does NOT apply.
    final double unboosted = StringUtils.jaroWinklerSimilarity("abxxxxx", "abzzzzz");
    final double rawJaroBelow = (2.0d / 7.0d + 2.0d / 7.0d + 1.0d) / 3.0d;
    assertEquals(rawJaroBelow, unboosted, EPSILON);

    // Prefix length is capped at 4 characters: a 6-char shared prefix receives the same 4-char boost factor (0.4).
    final double sixPrefix = StringUtils.jaroWinklerSimilarity("abcdefX", "abcdefY");
    final double rawSixJaro = (6.0d / 7.0d + 6.0d / 7.0d + 1.0d) / 3.0d;
    assertEquals(rawSixJaro + 0.1d * 4 * (1.0d - rawSixJaro), sixPrefix, EPSILON);
  }

  @Test
  public void jaroWinklerSimilarity_isSymmetric() {
    final String[][] pairs = {
      {"MaterialIcons-Regular", "MaterialIcons"},
      {"CupertinoIcons", "Cupertino"},
      {"MARTHA", "MARHTA"},
      {"DWAYNE", "DUANE"},
      {"DIXON", "DICKSONX"},
      {"a", "ab"},
    };
    for (String[] pair : pairs) {
      assertEquals(
        StringUtils.jaroWinklerSimilarity(pair[0], pair[1]),
        StringUtils.jaroWinklerSimilarity(pair[1], pair[0]),
        EPSILON
      );
    }
  }

  @Test
  public void jaroWinklerSimilarity_ranksMatchingFontFamilyAboveUnrelatedFonts() {
    final double exactFamily = StringUtils.jaroWinklerSimilarity("CupertinoIcons", "CupertinoIcons");
    final double regularVariant = StringUtils.jaroWinklerSimilarity("MaterialIcons-Regular", "MaterialIcons");
    final double unrelatedFont = StringUtils.jaroWinklerSimilarity("Roboto-Regular", "MaterialIcons");

    assertEquals(1.0d, exactFamily, EPSILON);
    assertTrue("Expected MaterialIcons-Regular to strongly match MaterialIcons", regularVariant > 0.90d);
    assertTrue("Expected matching font family to rank higher than unrelated font", regularVariant > unrelatedFont);
  }
}
