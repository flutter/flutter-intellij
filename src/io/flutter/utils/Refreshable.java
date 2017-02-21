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
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.Closeable;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A thread-safe variable that can be updated by submitting a callback to run in the background.
 *
 * <p>When the create callback is finished (and there is no newer task that makes it obsolete),
 * its value will be published and subscribers will be notified.
 *
 * <p>It's guaranteed that a Refreshable's visible state won't change while an event handler
 * is running on the Swing dispatch thread.
 */
public class Refreshable<T> implements Closeable {

  private final Schedule schedule = new Schedule();
  private final Publisher publisher;

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

  /**
   * The representation of this Refreshable in IntelliJ's dispose tree.
   *
   * <p>(Private so that nobody can add children.)
   */
  private final Disposable disposeNode;

  public Refreshable() {
    this(null);
  }

  /**
   * Creates a refreshable variable that reports when it stops using a value that it created.
   *
   * @param unpublish  will be called when the value is no longer in use. Can be called even though
   *                   the value was never published. It will run on the Swing dispatch thread.
   */
  public Refreshable(Consumer<T> unpublish) {
    this.publisher = new Publisher(unpublish);
    this.disposeNode = this::close;
  }

  /**
   * Returns the most recently published value, without waiting for any updates.
   *
   * <p>Returns null if the cache is uninitialized.
   *
   * <p>Calling getNow() twice during the same Swing event handler will return the same result.
   */
  public T getNow() {
    return publisher.get();
  }

  /**
   * Waits for the background task to finish, then returns the current value.
   *
   * <p>If {@link #refresh} is never called then this will block forever waiting
   * for the variable to be initialized.
   *
   * @throws IllegalStateException if called on the Swing dispatch thread.
   */
  public T getWhenReady() {
    if (SwingUtilities.isEventDispatchThread()) {
      throw new IllegalStateException("getWhenReady shouldn't be called from Swing dispatch thread");
    }

    publisher.waitForFirstValue();

    final Future refreshDone = busy.get();
    if (refreshDone == null) {
      return getNow(); // No background task; currently idle.
    }

    try {
      refreshDone.get();
    } catch (Exception e) {
      LOG.warn("Unexpected exception waiting for refresh task to finish", e);
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
      if (publisher.isClosing()) {
        throw new IllegalStateException("Can't subscribe to closed Refreshable");
      }
      subscribers.add(callback);
    }
  }

  /**
   * Stops notifications to a callback that was passed to {@link ::subscribe}.
   */
  public void unsubscribe(@NotNull Runnable callback) {
    synchronized (subscribers) {
      subscribers.remove(callback);
    }
  }

  /**
   * Creates and publishes a new value in the background.
   *
   * <p>The new value will be published if the callback finishes normally,
   * no newer refresh request is submitted in the meantime, and the value is different
   * (according to {#link Object#equals}) from the previous one.
   *
   * <p>The Callable interface doesn't provide any way to find out if the task was
   * cancelled. Instead, the callback can use {@link #isCancelled} to poll for this.
   *
   * <p>The callback should throw {@link CancellationException} to avoid publishing a
   * new value. (Any other exception will have the same effect, but a warning will
   * be logged.)
   */
  public void refresh(@NotNull Callable<T> create) {
    if (publisher.isClosing()) {
      LOG.warn("attempted to update closed Refreshable");
      return;
    }
    schedule.reschedule(create);

    // Start up the background task if it's not running.
    final FutureTask next = new FutureTask<>(this::runInBackground, null);
    if (busy.compareAndSet(null, next)) {
      // Wait until after event handler currently running, in case it calls refresh again.
      SwingUtilities.invokeLater(() -> AppExecutorUtil.getAppExecutorService().submit(next));
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
   * Starts shutting down the Refreshable.
   *
   * Asynchronously removes all subscribers, cancels any background tasks,
   * and unpublishes any values.
   */
  public void close() {
    if (!publisher.close()) {
      return; // already closed.
    }

    // Cancel any running create task.
    schedule.reschedule(null);

    // Remove from dispose tree. Calls close() again, harmlessly.
    Disposer.dispose(disposeNode);
  }

  /**
   * Automatically unsubscribes and unpublishes when a parent object is disposed.
   *
   * <p>Only one parent can be registered at a time. Auto-unsubscribe
   * will stop working for any object previously passed to this
   * method.
   */
  public void setDisposeParent(Disposable parent) {
    Disposer.register(parent, disposeNode);
  }

  /**
   * Runs requests until there are no more requests. Publishes the last successful response.
   */
  private void runInBackground() {
    try {
      // Yield to queued events.
      // Avoids unnecessary work if they change the schedule.
      try {
        SwingUtilities.invokeAndWait(() -> {});
      }
      catch (Exception e) {
        LOG.warn("Unexpected exception while updating a Refreshable", e);
        return;
      }

      for (Callable<T> request = schedule.next(); request != null; request = schedule.next()) {
        // Do the work.
        try {
          publisher.reschedule(request.call());
        } catch (CancellationException e) {
          // This is normal.
        } catch (Exception e) {
          if (!Objects.equal(e.getMessage(), "expected failure in test")) {
            LOG.warn("Callback threw an exception while updating a Refreshable", e);
          }
        } finally {
          schedule.done(request);
        }

        try {
          // Wait for an opportunity to publish.
          SwingUtilities.invokeAndWait(() -> {
            // If the schedule changed in the meantime, skip publishing the value.
            if (!schedule.hasNext()) {
              publisher.publish();
            }
          });
        }
        catch (Exception e) {
          LOG.warn("Unable to publish a value while updating a Refreshable", e);
        }
      }
    } finally {
      busy.set(null); // Allow restart on exit.
    }
  }

  private Set<Runnable> getSubscribers() {
    synchronized (subscribers) {
      return ImmutableSet.copyOf(subscribers);
    }
  }

  private static final Logger LOG = Logger.getInstance(Refreshable.class);

  /**
   * Manages the schedule for creating new values on the background thread.
   */
  private class Schedule {
    /**
     * The next request to run.
     *
     * <p>Null when there's nothing more to do.
     */
    private @Nullable Callable<T> scheduled;

    /**
     * The currently running create callback.
     *
     * <p>Null when nothing is currently running.
     */
    private @Nullable Callable<T> running;

    /**
     * If not null, the create callback has been cancelled.
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
     * Checks if there is any work scheduled.
     */
    synchronized boolean hasNext() {
      return scheduled != null;
    }

    /**
     * Returns the next task to run, or null if nothing is scheduled.
     */
    synchronized @Nullable Callable<T> next() {
      assert(running == null);
      running = scheduled;
      scheduled = null;
      return running;
    }

    /**
     * Indicates that we finished creating a value.
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

  /**
   * Manages the schedule for publishing and unpublishing values.
   */
  private class Publisher {
    private final @NotNull Consumer<T> unpublish;

    private @Nullable T scheduled;
    private @Nullable T published;

    /**
     * Completes when the first value is published.
     */
    private final FutureTask initialized = new FutureTask<>(() -> null);

    private boolean needToPublish;
    private boolean closing;

    Publisher(@Nullable Consumer<T> unpublish) {
      if (unpublish == null) unpublish = (x) -> {};
      this.unpublish = unpublish;
    }

    /**
     * Schedules a value to be published later, provided that it's not a duplicate.
     */
    synchronized void reschedule(@Nullable T toPublish) {
      if (closing) return;

      if (scheduled != null && !Objects.equal(toPublish, scheduled)) {
        final T old = scheduled;
        SwingUtilities.invokeLater(() -> unpublish.accept(old));
      }

      if (initialized.isDone() && Objects.equal(toPublish, published)) {
        // don't publish a duplicate
        scheduled = null;
        needToPublish = false;
      } else {
        scheduled = toPublish;
        needToPublish = true;
      }
    }

    synchronized boolean close() {
      if (closing) return false;
      reschedule(null);
      closing = true;
      publishAsync();
      return true;
    }

    /**
     * Publishes the next value synchronously.
     *
     * <p>Waits for any previously scheduled Swing event handlers to finish.
     */
    void waitAndPublish() {
      synchronized(this) {
        if (!needToPublish) return;
      }
      try {
        SwingUtilities.invokeAndWait(this::publish);
      }
      catch (Exception e) {
        LOG.warn("Unable to publish a value", e);
      }
    }

    void publishAsync() {
      synchronized(this) {
        if (!needToPublish) return;
      }
      SwingUtilities.invokeLater(this::publish);
    }

    void publish() {
      assert(SwingUtilities.isEventDispatchThread());

      final T discarded;
      synchronized (this) {
        if (!needToPublish) return;
        discarded = published;
        published = scheduled;
        needToPublish = false;
        scheduled = null;
        initialized.run();
      }

      if (discarded != null) {
        try {
          unpublish.accept(discarded);
        } catch (Exception e) {
          LOG.warn("An unpublish callback threw an exception while updating a Refreshable", e);
        }
      }

      final Set<Runnable> subscribers = getSubscribers();
      for (Runnable sub : subscribers) {
        try {
          sub.run();
        } catch (Exception e) {
          if (!Objects.equal(e.getMessage(), "expected failure in test")) {
            LOG.warn("A subscriber to a Refreshable threw an exception", e);
          }
        }
      }
    }

    void waitForFirstValue() {
      try {
        initialized.get();
      } catch (Exception e) {
        LOG.warn("Unexpected exception waiting for Refreshable to initialize", e);
      }
    }

    synchronized T get() {
      return published;
    }

    synchronized boolean isClosing() {
      return closing;
    }
  }
}
