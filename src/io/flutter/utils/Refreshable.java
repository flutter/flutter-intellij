/*
 * Copyright  2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A thread-safe variable that can be updated by submitting a callback to run in the background.
 *
 * <p>When the task is finished (and there is no newer task that makes it obsolete),
 * its value will be published and subscribers will be notified.
 */
public class Refreshable<T> implements Disposable {

  private final Schedule schedule = new Schedule();

  /**
   * Holds the most recently published value.
   */
  private final AtomicReference<T> published = new AtomicReference<>();

  /**
   * Completes when the first value is published.
   */
  private final FutureTask initialized = new FutureTask<>(() -> null);

  /**
   * Holds a future that completes when the background task exits.
   *
   * <p>Null when idle.
   */
  private final AtomicReference<Future> busy = new AtomicReference<>();

  /**
   * Subscribers to be notified after a value is published.
   *
   * <p>Access should be synchronized on the field.
   */
  private final Set<Runnable> subscribers = new LinkedHashSet<>();

  private final AtomicBoolean disposed = new AtomicBoolean();

  /**
   * Returns the most recently published value without waiting for any updates.
   *
   * <p>Returns null if the cache is uninitialized.
   */
  public T getNow() {
    return published.get();
  }

  /**
   * Waits for the first value to be published, then returns the current value.
   *
   * <p>If {@link #refresh} is never called then this will block forever.
   */
  public T getWhenInitialized() {
    try {
      initialized.get();
    } catch (Exception e) {
      LOG.warn("Unexpected exception waiting for Refreshable to initialize", e);
    }
    return getNow();
  }

  /**
   * Waits for the background task to finish, then returns the current value.
   *
   * <p>The background task may be kept busy for a long time due to long-running tasks
   * or frequent calls to {@link #refresh}. Therefore it shouldn't be called from the
   * Swing dispatch thread.
   */
  public T getWhenReady() {
    if (SwingUtilities.isEventDispatchThread()) {
      throw new IllegalStateException("getWhenReady shouldn't be called from Swing dispatch thread");
    }
    getWhenInitialized();

    final Future refreshDone = busy.get();
    if (refreshDone == null) {
      return getNow(); // Already idle.
    }

    try {
      refreshDone.get();
    } catch (Exception e) {
      LOG.warn("Unexpected exception waiting for refresh task to be idle", e);
    }
    return getNow();
  }

  /**
   * Runs a callback after the next value is published.
   *
   * <p>The callback will be run on the Swing dispatch thread.
   */
  public void whenPublished(@NotNull Runnable callback) {
    final Runnable once = new Runnable() {
      public void run() {
        unsubscribe(this);
        callback.run();
      }
    };
    subscribe(once);
  }

  /**
   * Runs a callback each time a new value is published.
   *
   * <p>The callback will be run on the Swing dispatch thread.
   *
   * <p>No notification will be sent if a published value is equal to the
   * previous one.
   */
  public void subscribe(@NotNull Runnable callback) {
    synchronized (subscribers) {
      if (disposed.get()) {
        throw new IllegalStateException("Can't subscribe to disposed Refreshable");
      }
      subscribers.add(callback);
    }
  }

  /**
   * Stops notifications for a callback that was passed to {@link ::subscribe}.
   */
  public void unsubscribe(@NotNull Runnable callback) {
    synchronized (subscribers) {
      subscribers.remove(callback);
    }
  }

  /**
   * Runs a callback in the background.
   *
   * <p>If the callback finishes normally, no newer refresh request is submitted in
   * the meantime, and its value is different from the previous one, the new value
   * will be published and subscribers will be notified.
   *
   * <p>The Callable interface doesn't provide any way to find out if the task was
   * cancelled. Instead, the callback can use {@link #isCancelled} to poll for this.
   *
   * <p>The callback should throw {@link CancellationException} to avoid publishing a
   * new value. (Any other exception will have the same effect, but a warning will
   * be logged.)
   */
  public void refresh(@NotNull Callable<T> callable) {
    if (disposed.get()) {
      throw new IllegalStateException("can't update disposed Refreshable");
    }
    schedule.reschedule(callable);

    // Start up the background task if it's not running.
    final FutureTask next = new FutureTask<>(this::runInBackground, null);
    if (busy.compareAndSet(null, next)) {
      AppExecutorUtil.getAppExecutorService().submit(next);
    }
  }

  /**
   * Returns true if the given callback was passed to the {@link #refresh}
   * method and is both running and cancelled.
   *
   * <p>(The callback can use this to poll for cancellation.)
   */
  public boolean isCancelled(Callable<T> callback) {
    return schedule.isCancelled(callback);
  }

  /**
   * Removes all subscribers and cancels any background tasks.
   */
  @Override
  public void dispose() {
    if (disposed.compareAndSet(false, true)) {
      synchronized (subscribers) {
        subscribers.clear();
      }
      schedule.reschedule(null);
    }
  }

  /**
   * Runs requests until there are no more requests. Publishes the last successful response.
   */
  private void runInBackground() {
    try {
      T response = null;
      boolean canPublish = false;
      for (Callable<T> request = schedule.next(); request != null; request = schedule.next()) {
        // Do the work.
        try {
          response = request.call();
          canPublish = true;
        } catch (CancellationException e) {
          // This is normal.
        } catch (Exception e) {
          if (!Objects.equal(e.getMessage(), "expected failure in test")) {
            LOG.warn("Task threw an exception while updating a Refreshable", e);
          }
          // Don't publish anything.
        } finally {
          schedule.done(request);
        }
      }
      if (canPublish) {
        publish(response);
      }
    } finally {
      busy.set(null); // Allow restart on exit.
    }
  }

  private void publish(T next) {
    final T prev = published.getAndSet(next);
    if (initialized.isDone() && Objects.equal(prev, next)) {
      return; // Debounce.
    }
    initialized.run();

    final Set<Runnable> subscribers = getSubscribers();
    if (subscribers.isEmpty()) {
      return;
    }

    // We are on a background thread. Deliver events on the Swing thread.
    // (We are still busy until subscribers are notified.)
    try {
      SwingUtilities.invokeAndWait(() -> {
        for (Runnable sub : subscribers) {
          try {
            sub.run();
          } catch (Exception e) {
            if (!Objects.equal(e.getMessage(), "expected failure in test")) {
              LOG.warn("A subscriber to a Refreshable threw an exception", e);
            }
          }
        }
      });
    }
    catch (Exception e) {
      LOG.warn("Unable to notify subscribers when updating a Refreshable", e);
    }
  }

  private Set<Runnable> getSubscribers() {
    synchronized (subscribers) {
      return ImmutableSet.copyOf(subscribers);
    }
  }

  private static final Logger LOG = Logger.getInstance(Refreshable.class);

  private class Schedule {
    /**
     * The next request to run.
     *
     * <p>Null when there's nothing more to do.
     */
    private @Nullable Callable<T> scheduled;

    /**
     * The currently running request.
     *
     * <p>Null when nothing is currently running.
     */
    private @Nullable Callable<T> running;

    /**
     * If not null, the running task is cancelled.
     */
    private @Nullable Callable<T> cancelled;

    /**
     * Replaces currently scheduled tasks with a new task.
     */
    synchronized void reschedule(@Nullable Callable<T> request) {
      scheduled = request;
      cancelled = running;
    }

    /**
     * Returns the next thing to do, or null if nothing is scheduled.
     */
    synchronized @Nullable Callable<T> next() {
      assert(running == null);
      running = scheduled;
      scheduled = null;
      return running;
    }

    /**
     * Marks a task as done.
     */
    synchronized void done(@NotNull Callable<T> request) {
      assert(running != null);
      running = null;
      cancelled = null;
    }

    synchronized boolean isCancelled(Callable<T> callback) {
      return callback == cancelled;
    }
  }
}
