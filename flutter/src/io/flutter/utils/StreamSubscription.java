/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.openapi.Disposable;

import java.util.function.Consumer;

public class StreamSubscription<T> implements Disposable {
  private final Consumer<T> onData;

  /**
   * Whether the onData callback should only be executed on the UI thread.
   */
  protected final boolean onUIThread;
  private EventStream<T> owner;
  private volatile boolean disposed = false;

  protected StreamSubscription(Consumer<T> onData, boolean onUIThread, EventStream<T> owner) {
    this.onData = onData;
    this.onUIThread = onUIThread;
    this.owner = owner;
  }

  @Override
  public void dispose() {
    disposed = true;
    if (owner != null) {
      owner.removeSubscription(this);
      owner = null;
    }
  }

  protected void notify(T value) {
    // There is a risk the call to dispose could have been interleaved
    // with calls on a different thread adding values to the stream so we suppress
    // calls to notify if the subscription has lready been closed so no events
    // get through after the dispose method is called.
    if (!disposed) {
      onData.accept(value);
    }
  }
}
