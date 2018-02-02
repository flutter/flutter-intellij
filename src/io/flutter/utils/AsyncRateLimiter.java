/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.google.common.util.concurrent.RateLimiter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.util.Alarm;

import javax.swing.*;
import java.util.concurrent.CompletableFuture;

import static io.flutter.utils.AsyncUtils.whenCompleteUiThread;

/**
 * Rate limiter that issues requests asynchronously on the ui thread
 * ensuring framesPerSecond rate is not exceeded and that no more than 1
 * request is issued at a time.
 * <p>
 * Methods from this class must only be invoked from the main UI thread.
 */
public class AsyncRateLimiter implements Disposable {
  private final RateLimiter rateLimiter;
  private final Alarm requestScheduler;
  private final Computable<CompletableFuture<?>> callback;
  private CompletableFuture<?> pendingRequest;
  /**
   * A request has been scheduled to run but is not yet pending.
   */
  private boolean requestScheduledButNotStarted;

  public AsyncRateLimiter(double framesPerSecond, Computable<CompletableFuture<?>> callback) {
    this.callback = callback;
    rateLimiter = RateLimiter.create(framesPerSecond);
    requestScheduler = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
  }

  public void scheduleRequest() {
    if (requestScheduledButNotStarted) {
      // No need to schedule a request if one has already been scheduled but
      // hasn't yet actually started executing.
      return;
    }

    if (pendingRequest != null && !pendingRequest.isDone()) {
      // Wait for the pending request to be done before scheduling the new
      // request. The existing request has already started so may return state
      // that is now out of date.
      whenCompleteUiThread(pendingRequest, (Object ignored, Throwable error) -> {
        pendingRequest = null;
        scheduleRequest();
      });
      return;
    }

    if (rateLimiter.tryAcquire()) {
      // Safe to perform the request immediately.
      performRequest();
    }
    else {
      // Track that we have sheduled a request and then schedule the request
      // to occur once the rate limiter is available.
      requestScheduledButNotStarted = true;
      requestScheduler.addRequest(() -> {
        rateLimiter.acquire();

        Runnable doRun = () -> {
          requestScheduledButNotStarted = false;
          performRequest();
        };
        if (ApplicationManager.getApplication() != null) {
          ApplicationManager.getApplication().invokeLater(doRun);
        }
        else {
          // This case existing to support unittesting.
          SwingUtilities.invokeLater(doRun);
        }
      }, 0);
    }
  }

  private void performRequest() {
    pendingRequest = callback.compute();
  }

  @Override
  public void dispose() {
  }
}
