/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

/**
 * Simple class for listening for a value that can be repeatedly updated.
 * <p>
 * The  benefit of using this class instead of listening for events is you
 * don't have to worry about missing values posted to the stream before your
 * code started listening which can lead to hard to find bugs.
 * <p>
 * The benefit of using this class over awaiting a Future is that the value can
 * update multiple times.
 * <p>
 * The value associated with the EventStream can be set on any thread.
 * <p>
 * The class is inspired by listen method on the Stream class in Dart.
 */
public class EventStream<T> {

  protected final HashSet<StreamSubscription<T>> subscriptions = new LinkedHashSet<>();

  private volatile T currentValue;

  public EventStream() {
    this(null);
  }

  public EventStream(T initialValue) {
    currentValue = initialValue;
  }

  public T getValue() {
    return currentValue;
  }

  /**
   * Returns whether the value was changed.
   */
  public boolean setValue(T value) {
    final List<StreamSubscription<T>> regularSubscriptions = new ArrayList<>();
    final List<StreamSubscription<T>> uiThreadSubscriptions = new ArrayList<>();
    synchronized (this) {
      if (currentValue == value) {
        return false;
      }
      currentValue = value;
      for (StreamSubscription<T> subscription : subscriptions) {
        if (subscription.onUIThread) {
          uiThreadSubscriptions.add(subscription);
        }
        else {
          regularSubscriptions.add(subscription);
        }
      }
      for (StreamSubscription<T> subscription : regularSubscriptions) {
        subscription.notify(value);
      }
    }
    if (!uiThreadSubscriptions.isEmpty()) {
      AsyncUtils.invokeLater(() -> {
        synchronized (this) {
          if (value != currentValue) {
            // This update is obsolete.
            return;
          }
          for (StreamSubscription<T> subscription : uiThreadSubscriptions) {
            subscription.notify(value);
          }
        }
      });
    }
    return true;
  }

  /**
   * Listens for changes to the value tracked by the EventStream.
   * onData is always called immediately with the current value specified
   * by the EventStream.
   *
   * @param onData is called every time the value associated with the EventStream changes.
   * @return a StreamSubscription object that is used to cancel the subscription.
   */
  public StreamSubscription<T> listen(Consumer<T> onData) {
    return listen(onData, false);
  }

  /**
   * Listens for changes to the value tracked by the EventStream.
   * onData is always called immediately with the current value specified
   * by the EventStream.
   *
   * @param onData     is called every time the value associated with the EventStream changes.
   * @param onUIThread specifies that callbacks are made on the UI thread.
   * @return a StreamSubscription object that is used to cancel the subscription.
   */
  public StreamSubscription<T> listen(Consumer<T> onData, boolean onUIThread) {
    final StreamSubscription<T> subscription = new StreamSubscription<>(onData, onUIThread, this);
    final T cachedCurrentValue;
    synchronized (this) {
      cachedCurrentValue = currentValue;
      subscriptions.add(subscription);
    }

    onData.accept(cachedCurrentValue);
    return subscription;
  }

  protected void removeSubscription(StreamSubscription<T> subscription) {
    synchronized (this) {
      subscriptions.remove(subscription);
    }
  }
}
