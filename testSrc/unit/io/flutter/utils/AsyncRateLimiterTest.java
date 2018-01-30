/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.Computable;
import org.junit.Before;
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

  final Clock clock = Clock.systemUTC();

  private AsyncRateLimiter rateLimiter;

  private final List<String> logEntries = new ArrayList<>();

  private int futureCompleteDelay = 0;
  private static final double TEST_FRAMES_PER_SECOND = 10.0;
  private static final long MS_PER_EVENT = (long)(1000.0 / TEST_FRAMES_PER_SECOND);

  private Computable<CompletableFuture<?>> callback;
  private CompletableFuture<Void> callbacksDone;
  private int expectedEvents;
  private int numEvents;

  @Before
  public void setUp() {
    callback = null;
    numEvents = 0;

    callbacksDone = new CompletableFuture<>();
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
    });
  }

  void scheduleRequest() {
    SwingUtilities.invokeLater(() -> {
      rateLimiter.scheduleRequest();
    });
  }

  @Test
  public void rateLimited() {
    final long start = clock.millis();
    expectedEvents = 4;

    while (!callbacksDone.isDone()) {
      // Schedule requests at many times the rate limit.
      scheduleRequest();
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
    final double requestsPerSecond = (expectedEvents - 1) / (delta * 0.001);
    assertTrue("Requests per second less than limit. Actual: " + requestsPerSecond, requestsPerSecond < TEST_FRAMES_PER_SECOND);
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
    final double requestsPerSecond = (expectedEvents - 1) / (delta * 0.001);
    assertTrue("Requests per second less than 10 times limit. Actual: " + requestsPerSecond,
               requestsPerSecond * 10 < TEST_FRAMES_PER_SECOND);
    // We use a large delta so that tests run under load do not result in flakes.
    assertTrue("Requests per second within 5 fps of rate limit. ACTUAL: " + requestsPerSecond,
               requestsPerSecond * 10 + 5.0 > TEST_FRAMES_PER_SECOND);
  }

  private synchronized boolean log(String message) {
    return logEntries.add(message);
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
