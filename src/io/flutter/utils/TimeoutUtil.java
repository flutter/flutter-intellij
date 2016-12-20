/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

public class TimeoutUtil {
  private TimeoutUtil() {
  }

  public static void executeWithTimeout(long timeout, @NotNull final Runnable run) {
    final long sleep = 50;
    final long start = System.currentTimeMillis();
    final AtomicBoolean done = new AtomicBoolean(false);
    final Thread thread = new Thread("Fast Function Thread@" + run.hashCode()) {
      public void run() {
        try {
          run.run();
        }
        catch (ThreadDeath ignored) {

        }
        finally {
          done.set(true);
        }
      }
    };
    thread.start();

    while (!done.get() && System.currentTimeMillis() - start < timeout) {
      try {
        Thread.sleep(sleep);
      }
      catch (InterruptedException var10) {
        break;
      }
    }

    if (!thread.isInterrupted()) {
      //noinspection deprecation
      thread.stop();
    }
  }

  public static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    }
    catch (InterruptedException ignored) {

    }
  }

  public static long getDurationMillis(long startNanoTime) {
    return (System.nanoTime() - startNanoTime) / 1000000L;
  }
}
