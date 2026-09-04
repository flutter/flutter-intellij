/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CollectionUtils {
  private CollectionUtils() {
  }

  public static <T> boolean anyMatch(@NotNull T[] in, @NotNull final Predicate<T> predicate) {
    return anyMatch(Arrays.stream(in), predicate);
  }

  public static <T> boolean anyMatch(@NotNull final List<T> in, @NotNull final Predicate<T> predicate) {
    return anyMatch(in.stream(), predicate);
  }

  public static <T> boolean anyMatch(@NotNull final Stream<T> in, @NotNull final Predicate<T> predicate) {
    return in.anyMatch(predicate);
  }

  public static <T> List<T> filter(@NotNull T[] in, @NotNull final Predicate<T> predicate) {
    return filter(Arrays.stream(in), predicate);
  }

  public static <T> List<T> filter(@NotNull final List<T> in, @NotNull final Predicate<T> predicate) {
    return filter(in.stream(), predicate);
  }

  public static <T> List<T> filter(@NotNull final Stream<T> in, @NotNull final Predicate<T> predicate) {
    return in
      .filter(predicate)
      .collect(Collectors.toList());
  }
}
