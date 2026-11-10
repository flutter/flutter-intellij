/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * Stateless string comparison and similarity utilities.
 *
 * <p>Why not import {@code info.debatty.java.stringsimilarity.JaroWinkler}? Historically this
 * plugin imported {@code JaroWinkler} without declaring {@code java-string-similarity} as a direct
 * plugin dependency because IntelliJ's bundled {@code intellij.spellchecker} module happened to
 * expose it transitively on the classpath. Starting in Android Studio 2026.2 ({@code 262}),
 * {@code intellij.spellchecker} no longer bundles {@code java-string-similarity}, causing IntelliJ
 * Plugin Verifier to fail with a {@code NoSuchClassError} compatibility problem (and throwing
 * {@link NoClassDefFoundError} at runtime). Implementing standard Jaro-Winkler directly in pure
 * Java avoids fragile transitive IDE classpath coupling and third-party dependency shading.
 */
public final class StringUtils {
  private static final double JARO_WINKLER_BOOST_THRESHOLD = 0.7d;
  private static final double JARO_WINKLER_PREFIX_SCALE = 0.1d;
  private static final int JARO_WINKLER_MAX_PREFIX_LENGTH = 4;

  private StringUtils() {
  }

  /**
   * Computes standard Jaro-Winkler string similarity in {@code [0.0, 1.0]} using the classic
   * Winkler boost threshold ({@code 0.7}), prefix scaling factor ({@code 0.1}), and maximum shared
   * prefix length ({@code 4}).
   *
   * <p>Maintained in-tree rather than importing {@code info.debatty.java.stringsimilarity.JaroWinkler}
   * so the plugin passes IntelliJ Plugin Verifier across IDE branches ({@code 262}+) that no longer
   * expose {@code java-string-similarity} on the platform classpath.
   */
  public static double jaroWinklerSimilarity(@NotNull String first, @NotNull String second) {
    Objects.requireNonNull(first, "first must not be null");
    Objects.requireNonNull(second, "second must not be null");
    if (first.equals(second)) {
      return 1.0d;
    }
    if (first.isEmpty() || second.isEmpty()) {
      return 0.0d;
    }

    final String shorter = first.length() <= second.length() ? first : second;
    final String longer = first.length() <= second.length() ? second : first;

    final int matchWindow = Math.max(longer.length() / 2 - 1, 0);
    final int[] shorterMatchIndexes = new int[shorter.length()];
    Arrays.fill(shorterMatchIndexes, -1);
    final boolean[] longerMatches = new boolean[longer.length()];

    int matches = 0;
    for (int i = 0; i < shorter.length(); i++) {
      final char ch = shorter.charAt(i);
      final int start = Math.max(i - matchWindow, 0);
      final int end = Math.min(i + matchWindow + 1, longer.length());
      for (int j = start; j < end; j++) {
        if (!longerMatches[j] && ch == longer.charAt(j)) {
          shorterMatchIndexes[i] = j;
          longerMatches[j] = true;
          matches++;
          break;
        }
      }
    }
    if (matches == 0) {
      return 0.0d;
    }

    final char[] matchedShorter = new char[matches];
    final char[] matchedLonger = new char[matches];
    for (int i = 0, index = 0; i < shorter.length(); i++) {
      if (shorterMatchIndexes[i] != -1) {
        matchedShorter[index++] = shorter.charAt(i);
      }
    }
    for (int j = 0, index = 0; j < longer.length(); j++) {
      if (longerMatches[j]) {
        matchedLonger[index++] = longer.charAt(j);
      }
    }

    int halfTranspositions = 0;
    for (int i = 0; i < matches; i++) {
      if (matchedShorter[i] != matchedLonger[i]) {
        halfTranspositions++;
      }
    }

    int commonPrefix = 0;
    final int maxPrefix = Math.min(JARO_WINKLER_MAX_PREFIX_LENGTH, shorter.length());
    for (int i = 0; i < maxPrefix; i++) {
      if (first.charAt(i) == second.charAt(i)) {
        commonPrefix++;
      }
      else {
        break;
      }
    }

    final int transpositions = halfTranspositions / 2;
    final double jaro =
      ((double)matches / first.length() +
       (double)matches / second.length() +
       (double)(matches - transpositions) / matches) / 3.0d;
    if (jaro < JARO_WINKLER_BOOST_THRESHOLD) {
      return jaro;
    }
    return jaro + JARO_WINKLER_PREFIX_SCALE * commonPrefix * (1.0d - jaro);
  }
}
