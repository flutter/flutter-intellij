/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.google.common.util.concurrent.Uninterruptibles;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

public class ThreadUtilTest {
  @Test
  public void simple() {
    final CountDownLatch stopLatch = new CountDownLatch(1);
    try {
      final Thread newThread = new Thread(() -> Uninterruptibles.awaitUninterruptibly(stopLatch));
      newThread.start();

      final List<Thread> withNewThread = ThreadUtil.getCurrentGroupThreads();
      assertThat(withNewThread, hasItem(newThread));

      // Ask the new thread to stop and wait for it.
      stopLatch.countDown();
      for (int i = 0; i < 1000; i++) {
        if (!newThread.isAlive()) {
          break;
        }
        Uninterruptibles.sleepUninterruptibly(10, TimeUnit.MILLISECONDS);
      }

      final List<Thread> withoutNewThread = ThreadUtil.getCurrentGroupThreads();
      assertThat(withoutNewThread, not(hasItem(newThread)));
    }
    finally {
      stopLatch.countDown();
    }
  }
}
