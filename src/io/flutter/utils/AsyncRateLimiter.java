/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.google.common.util.concurrent.RateLimiter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.util.Alarm;
import io.flutter.view.InspectorPanel;

import javax.swing.*;
import java.util.concurrent.CompletableFuture;

/**
 * Rate limiter that issues requests asynchronously on the ui thread
 * ensuring framesPerSecond rate is not exceeded and that no more than 1
 * request is issued at a time.
 * <p>
 * Methods from this class must only be invoked from the main UI thread.
 */
public class AsyncRateLimiter {
  private static final Logger LOG = Logger.getInstance(AsyncRateLimiter.class);
  private final String requestScheduleLock = "requestScheduleLock";
  private final RateLimiter rateLimiter;
  private final Alarm requestScheduler;
  private final Computable<CompletableFuture<?>> callback;
  /**
   * Number of requests pending including the request currently executing.
   */
  private volatile int numRequestsPending;
  /**
   * Number of requests currently executing.
   *
   * This should only be 0 or 1 but we track it as an integer to make it easier
   * to catch logic bugs.
   */
  private volatile int numRequestsExecuting;

  public AsyncRateLimiter(double framesPerSecond, Computable<CompletableFuture<?>> callback, Disposable parentDisposable) {
    this.callback = callback;
    rateLimiter = RateLimiter.create(framesPerSecond);
    requestScheduler = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, parentDisposable);
  }

  public void scheduleRequest() {
    final boolean scheduleRequestImmediately;
    synchronized (requestScheduleLock) {
      scheduleRequestImmediately = numRequestsPending == 0;
      assert (numRequestsPending >= 0); // Logic error if we ever complete more requests than we started.
      assert (numRequestsPending < 3); // Logic error if we ever have more than 2 requests pending.
      assert (numRequestsExecuting < 2); // Logic error if we ever have more than 1 request executing.
      final int numRequestsNotYetStarted = numRequestsPending - numRequestsExecuting;
      // If there is alredy a request that has yet to begin, no need to schedule new request.
      if (numRequestsNotYetStarted == 0) {
        numRequestsPending++;
      }
    }
    if (scheduleRequestImmediately) {
      scheduleRequestHelper();
    }
  }

  // This method may be called on any thread.
  public void scheduleRequestHelper() {
    // Don't schedule the request if this rate limiter has been disposed.
    if (requestScheduler.isDisposed()) {
      return;
    }

    // Schedule a request to occur once the rate limiter is available.
    requestScheduler.addRequest(() -> {
      rateLimiter.acquire();
      performRequestOnUiThread();
    }, 0);
  }

  private void performRequestOnUiThread() {
    final Runnable doRun = () -> {
      if (requestScheduler.isDisposed()) {
        return;
      }
      performRequest();
    };
    final Application app = ApplicationManager.getApplication();
    if (app == null || app.isUnitTestMode()) {
      // This case exists to support unittesting.
      SwingUtilities.invokeLater(doRun);
    }
    else {
      app.invokeLater(doRun);
    }
  }

  private void performRequest() {
    synchronized (requestScheduleLock) {
      assert (numRequestsPending > 0);
      assert (numRequestsExecuting == 0);
      numRequestsExecuting++;
    }
    try {
       final CompletableFuture<?> requestComplete = callback.compute();
       requestComplete.whenCompleteAsync(
         (v, e) -> onRequestComplete());
     } catch (Exception e) {
      LOG.warn(e);
      onRequestComplete();
    }
  }

  private void onRequestComplete() {
    boolean requestsPending;
    synchronized (requestScheduleLock) {
      assert (numRequestsPending > 0);
      assert (numRequestsExecuting == 1);
      numRequestsPending--;
      numRequestsExecuting--;
      requestsPending = numRequestsPending > 0;
    }
    if (requestsPending) {
      scheduleRequestHelper();
    }
  }
}
