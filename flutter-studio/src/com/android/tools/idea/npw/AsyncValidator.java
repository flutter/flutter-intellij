/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.npw;

import com.android.annotations.concurrency.GuardedBy;
import com.intellij.openapi.application.Application;
import org.jetbrains.annotations.NotNull;

/**
 * Simple utility that reruns task in another thread and reports the result back.
 * <p/>
 * The goal is to run validation in a non-UI thread. Some validation results
 * may be dropped as user input may change before validation completes.
 * <p/>
 * Some threading information:
 * invalidate()           is called by client from any thread to signal that
 *                        user input had changed and needs to be invalidated.
 *                        This will mark this validator as dirty and schedule
 *                        a runnable in the background thread if there is none
 *                        pending/running.
 *
 * validate()             will be called on a background thread to obtain
 *                        the validation result. This call can be repeated
 *                        if the data is marked dirty before a call to
 *                        validate() completes.
 *
 * showValidationResult() will be called on the UI thread with the data
 *                        returned by validate() called on a background thread.
 *                        Note that that data may be dropped if invalidate()
 *                        was called at some point while the UI thread task
 *                        was pending.
 */
public abstract class AsyncValidator<V> {
  @NotNull private final Application myApplication;
  private final ResultReporter myResultReporter = new ResultReporter();
  @GuardedBy("this")
  private boolean myIsDirty = false;
  @GuardedBy("this")
  private boolean myIsScheduled = false;

  public AsyncValidator(@NotNull Application application) {
    myApplication = application;
  }

  /**
   * Informs the validator that data had updated and validation status needs to be recomputed.
   * <p/>
   * Can be called on any thread.
   */
  public synchronized final void invalidate() {
    myIsDirty = true;
    myResultReporter.setDirty();
    if (!myIsScheduled) {
      myIsScheduled = true;
      myApplication.executeOnPooledThread(this::revalidateUntilClean);
    }
  }

  /**
   * Runs validation on the background thread, repeating it if it was reported
   * that data was updated.
   */
  private void revalidateUntilClean() {
    V result;
    do {
      markClean();
      result = validate();
    }
    while (!submit(result));
  }

  /**
   * Submit will be canceled if the validator was marked dirty since we cleared the flag.
   */
  private synchronized boolean submit(@NotNull V result) {
    myIsScheduled = myIsDirty;
    if (!myIsScheduled) {
      myResultReporter.report(result);
      return true;
    }
    else {
      return false;
    }
  }

  /**
   * Marks validator status clean, meaning the validation result is in sync
   * with user input.
   */
  private synchronized void markClean() {
    myIsDirty = false;
  }

  /**
   * Invoked on UI thread to show "stable" validation result in the UI.
   */
  protected abstract void showValidationResult(@NotNull V result);

  /**
   * Invoked on a validation thread to perform long-running operation.
   */
  @NotNull
  protected abstract V validate();

  /**
   * Sent to main thread to report result of the background operation.
   */
  private final class ResultReporter implements Runnable {
    @GuardedBy("this")
    private V myResult = null;
    @GuardedBy("this")
    private boolean myIsPending = false;

    public synchronized void report(@NotNull V value) {
      myResult = value;
      if (!myIsPending) {
        myIsPending = true;
        myApplication.invokeLater(this, myApplication.getAnyModalityState());
      }
    }

    @Override
    public synchronized void run() {
      V result = myResult;
      myResult = null;
      try {
        if (result != null) {
          showValidationResult(result);
        }
      } finally {
        myIsPending = false;
      }
    }

    public synchronized void setDirty() {
      myResult = null;
    }
  }
}
