/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ThreadUtil {
  public static ThreadGroup getRootThreadGroup() {
    ThreadGroup threadGroup = Thread.currentThread().getThreadGroup();
    ThreadGroup parentThreadGroup;
    while ((parentThreadGroup = threadGroup.getParent()) != null) {
      threadGroup = parentThreadGroup;
    }
    return threadGroup;
  }

  /**
   * Returns all active threads of the current group.
   */
  public static List<Thread> getAllThreads() {
    final ThreadGroup rootThreadGroup = getRootThreadGroup();

    Thread[] threads = new Thread[rootThreadGroup.activeCount()];
    while (rootThreadGroup.enumerate(threads, true) == threads.length) {
      threads = new Thread[threads.length * 2];
    }

    return getThreadList(threads);
  }

  /**
   * Returns all active threads of the current group.
   */
  public static List<Thread> getCurrentGroupThreads() {
    Thread[] threads = new Thread[Thread.activeCount()];
    while (Thread.enumerate(threads) == threads.length) {
      threads = new Thread[threads.length * 2];
    }

    return getThreadList(threads);
  }

  /**
   * Return the list of thread in the given array, which ends with null.
   */
  @NotNull
  private static List<Thread> getThreadList(Thread[] threads) {
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
