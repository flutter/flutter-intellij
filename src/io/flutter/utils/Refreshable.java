/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
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
 * <p>When the callback is finished (and there is no newer task that makes it obsolete),
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
  private final AtomicReference<Future> backgroundTask = new AtomicReference<>();

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
  public @Nullable T getNow() {
    return publisher.get();
  }

  /**
   * Returns whether the Refreshable is busy, idle, or closed.
   */
  public State getState() {
    return publisher.state.get();
  }

  /**
   * Waits for the background task to finish or the Refreshable to be closed, then returns the current value.
   *
   * <p>If {@link #refresh} is never called then this will block forever waiting
   * for the variable to be initialized.
   *
   * @throws IllegalStateException if called on the Swing dispatch thread.
   */
  public @Nullable T getWhenReady() {
    if (SwingUtilities.isEventDispatchThread()) {
      throw new IllegalStateException("getWhenReady shouldn't be called from Swing dispatch thread");
    }

    publisher.waitForFirstValue();

    final Future refreshDone = backgroundTask.get();
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
   * Runs a callback whenever the Refreshable's value or state changes.
   *
   * <p>The callback will be run on the Swing dispatch thread.
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
   * <p>(Convenience method for when a {@link Request} isn't needed.)
   */
  public void refresh(@NotNull Callable<T> callback) {
    refresh((x) -> callback.call());
  }

  /**
   * Creates and publishes a new value in the background.
   *
   * <p>If an exception is thrown, no new value will be published, but
   * a message will be logged.
   */
  public void refresh(@NotNull Callback<T> callback) {
    if (publisher.isClosing()) {
      LOG.warn("attempted to update closed Refreshable");
      return;
    }
    schedule.reschedule(new Request<>(this, callback));

    // Start up the background task if it's not running.
    final FutureTask next = new FutureTask<>(this::runInBackground, null);
    if (backgroundTask.compareAndSet(null, next)) {
      // Wait until after event handler currently running, in case it calls refresh again.
      SwingUtilities.invokeLater(() -> AppExecutorUtil.getAppExecutorService().submit(next));
    }
  }

  /**
   * Asynchronously shuts down the Refreshable.
   *
   * <p>Sets the published value to null and cancels any background tasks.
   *
   * <p>Also sets the state to CLOSED and notifies subscribers. Removes subscribers after delivering the last event.
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
      publisher.setState(State.BUSY);

      for (Request<T> request = schedule.next(); request != null; request = schedule.next()) {
        // Do the work.
        try {
          final T value = request.callback.call(request);
          publisher.reschedule(value);
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
              if (publisher.publish()) {
                publisher.fireEvent();
              }
            }
          });
        } catch (Exception e) {
          LOG.warn("Unable to publish a value while updating a Refreshable", e);
        }
      }
    } finally {
      publisher.setState(State.IDLE);
      backgroundTask.set(null); // Allow restart on exit.
    }
  }

  private static final Logger LOG = Logger.getInstance(Refreshable.class);

  /**
   * A value indicating whether the Refreshable is being updated or not.
   */
  public enum State { BUSY, IDLE, CLOSED }

  /**
   * A function that produces the next value of a Refreshable.
   */
  public interface Callback<T> {
    /**
     * Calculates the new value.
     *
     * <p>If no update is needed, it should either return the previous value or
     * throw {@link CancellationException} to avoid publishing a new value.
     * (Any other exception will have the same effect, but a warning will be logged.)
     */
    T call(Request req) throws Exception;
  }

  /**
   * A scheduled or running refresh request.
   */
  public static class Request<T> {
    private final Refreshable<T> target;
    private final Callback<T> callback;

    Request(Refreshable<T> target, Callback<T> callback) {
      this.target = target;
      this.callback = callback;
    }

    /**
     * Returns true if the value is no longer needed, because another request
     * is ready to run.
     *
     * <p>When the request is cancelled, the caller can either return the previous
     * value or throw {@link CancellationException} to avoid publishing a new
     * value.
     */
    public boolean isCancelled() {
      return target.schedule.isCancelled(this);
    }

    /**
     * The value returned by the most recent successful request.
     * (It might not be published yet.)
     */
    public T getPrevious() {
      return target.publisher.getPrevious();
    }
  }

  /**
   * Manages the schedule for creating new values on the background thread.
   */
  private class Schedule {
    /**
     * The next request to run.
     *
     * <p>Null when there's nothing more to do.
     */
    private @Nullable Request<T> scheduled;

    /**
     * The currently running create callback.
     *
     * <p>Null when nothing is currently running.
     */
    private @Nullable Request<T> running;

    /**
     * If not null, the create callback has been cancelled.
     */
    private @Nullable Request<T> cancelled;

    /**
     * Replaces currently scheduled tasks with a new task.
     */
    synchronized void reschedule(@Nullable Request<T> request) {
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
    synchronized @Nullable Request<T> next() {
      assert(running == null);
      running = scheduled;
      scheduled = null;
      return running;
    }

    /**
     * Indicates that we finished creating a value.
     */
    synchronized void done(@NotNull Request<T> request) {
      assert(running != null);
      running = null;
      cancelled = null;
    }

    synchronized boolean isCancelled(Request<T> callback) {
      return callback == cancelled;
    }
  }

  /**
   * Manages the schedule for publishing and unpublishing values.
   */
  private class Publisher {
    private final @NotNull Consumer<T> unpublishCallback;

    private @Nullable T scheduled;
    private @Nullable T published;

    private final AtomicReference<State> state = new AtomicReference<>(State.IDLE);

    /**
     * Completes when the first value is published or the Refreshable is closed.
     */
    private final FutureTask initialized = new FutureTask<>(() -> null);

    private boolean needToPublish;
    private boolean closing;

    Publisher(@Nullable Consumer<T> unpublish) {
      if (unpublish == null) unpublish = (x) -> {};
      this.unpublishCallback = unpublish;
    }

    /**
     * Schedules a value to be published later, provided that it's not a duplicate.
     */
    synchronized void reschedule(@Nullable T toPublish) {
      if (scheduled != null && Objects.equal(toPublish, scheduled)) {
        return; // Duplicate already scheduled. Nothing to do.
      }

      final T discarded = unschedule();
      if (discarded != null) {
        SwingUtilities.invokeLater(() -> unpublish(discarded));
      }

      // Don't publish a duplicate. (Don't unpublish a duplicate either.)
      if (initialized.isDone() && Objects.equal(toPublish, published)) {
        needToPublish = false;
        return;
      }

      if (closing) {
        // Don't publish anything else since we're closing.
        // Discard new value instead of publishing it.
        if (toPublish != null) {
          SwingUtilities.invokeLater(() -> unpublish(toPublish));
        }
        return;
      }

      scheduled = toPublish;
      needToPublish = true;
    }

    /**
     * Remove any previously scheduled value, returning it.
     */
    private synchronized T unschedule() {
      final T old = scheduled;
      scheduled = null;
      needToPublish = false;
      return old;
    }

    /**
     * Returns the value that was scheduled most recently.
     */
    private synchronized T getPrevious() {
      if (needToPublish) {
        return scheduled;
      } else {
        return published;
      }
    }

    boolean close() {
      final T wasScheduled;
      synchronized (this) {
        if (closing) return false;
        wasScheduled = unschedule();
        closing = true;
      }
      SwingUtilities.invokeLater(() -> {
        unpublish(wasScheduled);

        final T wasPublished;
        synchronized (this) {
          wasPublished = published;
          published = null;
        }
        unpublish(wasPublished);
        setState(State.CLOSED);

        // Free subscribers. (Avoid memory leaks.)
        synchronized (subscribers) {
          subscribers.clear();
        }

        // unblock getWhenReady() if no value was ever published.
        initialized.run();
      });
      return true;
    }

    /**
     * Returns true if the value was published.
     */
    boolean publish() {
      assert(SwingUtilities.isEventDispatchThread());

      final T discarded;
      synchronized (this) {
        if (!needToPublish) return false;
        discarded = published;
        published = unschedule();
        needToPublish = false;
        initialized.run();
      }

      if (discarded != null) {
        unpublish(discarded);
      }

      return true;
    }

    void unpublish(@Nullable T discarded) {
      assert(SwingUtilities.isEventDispatchThread());
      if (discarded == null) return;
      try {
        unpublishCallback.accept(discarded);
      } catch (Exception e) {
        LOG.warn("An unpublish callback threw an exception while updating a Refreshable", e);
      }
    }

    void setState(State newState) {
      if (SwingUtilities.isEventDispatchThread()) {
        doSetState(newState);
        return;
      }

      try {
        SwingUtilities.invokeAndWait(() -> doSetState(newState));
      } catch (Exception e) {
        LOG.error("Unable to change state of Refreshable", e);
      }
    }

    private void doSetState(State newState) {
      final State oldState = state.getAndSet(newState);
      if (oldState == newState) return; // debounce
      fireEvent();
    }

    private void fireEvent() {
      assert SwingUtilities.isEventDispatchThread();
      for (Runnable sub : getSubscribers()) {
        try {
          sub.run();
        } catch (Exception e) {
          if (!Objects.equal(e.getMessage(), "expected failure in test")) {
            LOG.warn("A subscriber to a Refreshable threw an exception", e);
          }
        }
      }
    }

    private Set<Runnable> getSubscribers() {
      synchronized (subscribers) {
        return ImmutableSet.copyOf(subscribers);
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
