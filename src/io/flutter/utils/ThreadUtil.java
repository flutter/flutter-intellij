/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import java.util.ArrayList;
import java.util.List;

public class ThreadUtil {
  /**
   * Returns all active threads of the current group.
   */
  public static List<Thread> getCurrentGroupThreads() {
    Thread[] threads = new Thread[Thread.activeCount()];
    while (Thread.enumerate(threads) == threads.length) {
      threads = new Thread[threads.length * 2];
    }

    final List<Thread> result = new ArrayList<>(threads.length);
    for (Thread thread : threads) {
      if (thread == null) {
        break;
      }
      result.add(thread);
    }

    return result;
  }
}
