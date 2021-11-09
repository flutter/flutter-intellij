/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.CompositeDisposable;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import javax.swing.*;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.supplyAsync;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class AsyncRateLimiterTest {

  private static final double TEST_FRAMES_PER_SECOND = 10.0;
  private static final long MS_PER_EVENT = (long)(1000.0 / TEST_FRAMES_PER_SECOND);
  final Clock clock = Clock.systemUTC();
  private final List<String> logEntries = new ArrayList<>();
  private AsyncRateLimiter rateLimiter;
  private Computable<CompletableFuture<?>> callback;
  private CompletableFuture<Void> callbacksDone;
  private CompositeDisposable disposable;
  private int expectedEvents;
  private volatile int numEvents;

  @Before
  public void setUp() {
    callback = null;
    numEvents = 0;

    callbacksDone = new CompletableFuture<>();
    disposable = new CompositeDisposable();
    rateLimiter = new AsyncRateLimiter(TEST_FRAMES_PER_SECOND, () -> {
      if (!SwingUtilities.isEventDispatchThread()) {
        log("subscriber should be called on Swing thread");
        callbacksDone.complete(null);
        return null;
      }

      log("EVENT");

      CompletableFuture<?> ret = null;
      if (callback != null) {
        ret = callback.compute();
      }
      if (ret == null) {
        ret = new CompletableFuture<>();
        ret.complete(null);
      }
      numEvents++;
      if (numEvents == expectedEvents) {
        log("DONE");
        callbacksDone.complete(null);
      }
      else if (numEvents > expectedEvents) {
        log("unexpected number of events fired");
      }
      return ret;
    }, disposable);
  }

  @After
  public void tearDown() {
    Disposer.dispose(disposable);
  }

  void scheduleRequest() {
    rateLimiter.scheduleRequest();
  }

  @Test
  @Ignore("generally flaky")
  public void rateLimited() {
    final long start = clock.millis();
    expectedEvents = 4;

    callback = null;
    while (!callbacksDone.isDone()) {
      // Schedule requests at many times the rate limit.
      SwingUtilities.invokeLater(() -> {
        if (!callbacksDone.isDone()) {
          rateLimiter.scheduleRequest();
        }
      });

      try {
        Thread.sleep(1);
      }
      catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    final long current = clock.millis();
    final long delta = current - start;

    checkLog("EVENT", "EVENT", "EVENT", "EVENT", "DONE");

    // First event should occur immediately so don't count it.
    // The last event just fired so add in MS_PER_EVENT to factor in that it
    // will be that long before the next event fires.
    final double requestsPerSecond = (numEvents - 1) / ((delta + MS_PER_EVENT) * 0.001);

    assertTrue("Requests per second does not exceed limit. Actual: " + requestsPerSecond,
               requestsPerSecond <= TEST_FRAMES_PER_SECOND);

    // We use a large delta so that tests run under load do not result in flakes.
    assertTrue("Requests per second within 3 fps of rate limit:", requestsPerSecond + 3.0 > TEST_FRAMES_PER_SECOND);
  }

  @Test
  public void rateLimitedSlowNetwork() {
    // In this test we simulate the network requests triggered for each
    // request being slow resulting in a lower frame rate as the network
    // now becomes the limiting factor instead of the rate limiter
    // as the rate limiter should never issue a request before the
    // previous request completed.
    final long start = clock.millis();
    expectedEvents = 3;
    callback = () -> {
      // Delay 10 times the typical time between events before returning.
      return supplyAsync(() -> {
        try {
          Thread.sleep(MS_PER_EVENT * 10);
        }
        catch (InterruptedException e) {
          reportFailure(e);
        }
        return null;
      });
    };

    while (!callbacksDone.isDone()) {
      // Schedule requests at many times the rate limit.
      scheduleRequest();
      try {
        Thread.sleep(1);
      }
      catch (InterruptedException e) {
        reportFailure(e);
      }
    }

    final long current = clock.millis();
    final long delta = current - start;

    checkLog("EVENT", "EVENT", "EVENT", "DONE");

    // First event should occur immediately so don't count it.
    // The last event just fired so add in MS_PER_EVENT to factor in that it
    // will be that long before the next event fires.
    final double requestsPerSecond = (expectedEvents - 1) / ((delta + MS_PER_EVENT) * 0.001);
    assertTrue("Requests per second less than 10 times limit. Actual: " + requestsPerSecond,
               requestsPerSecond * 10 < TEST_FRAMES_PER_SECOND);
    // We use a large delta so that tests run under load do not result in flakes.
    assertTrue("Requests per second within 5 fps of rate limit. ACTUAL: " + requestsPerSecond,
               requestsPerSecond * 10 + 5.0 > TEST_FRAMES_PER_SECOND);
  }

  @Test
  public void avoidUnneededRequests() {
    // In this test we verify that we don't accidentally schedule unneeded

    final long start = clock.millis();
    expectedEvents = 1;
    callback = () -> {
      // Make the first event slow but other events instant.
      // This will make it easier to catch if we accidentally schedule a second
      // request when we shouldn't.
      if (numEvents == 0) {
        try {
          Thread.sleep(MS_PER_EVENT);
        }
        catch (InterruptedException e) {
          reportFailure(e);
        }
      }
      return null;
    };

    // Schedule 4 requests immediatelly one after each other and verify that
    // only one is actually triggered.
    scheduleRequest();
    scheduleRequest();
    scheduleRequest();
    scheduleRequest();

    // Extra sleep to ensure no unexpected events fire.
    try {
      Thread.sleep(MS_PER_EVENT * 10);
    }
    catch (InterruptedException e) {
      reportFailure(e);
    }

    assertEquals(numEvents, expectedEvents);
  }

  private synchronized void log(String message) {
    logEntries.add(message);
  }

  private synchronized List<String> getLogEntries() {
    return ImmutableList.copyOf(logEntries);
  }

  private void reportFailure(Exception e) {
    fail("Exception: " + e + "\nLog: " + getLogEntries().toString());
  }

  private void checkLog(String... expectedEntries) {
    assertThat("logEntries entries are different", getLogEntries(), is(ImmutableList.copyOf(expectedEntries)));
    logEntries.clear();
  }
}
