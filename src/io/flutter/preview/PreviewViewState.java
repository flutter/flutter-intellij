/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import com.intellij.util.EventDispatcher;

import javax.swing.event.ChangeListener;

/**
 * State for the Preview view.
 */
public class PreviewViewState {
  private final EventDispatcher<ChangeListener> dispatcher = EventDispatcher.create(ChangeListener.class);

  public PreviewViewState() {
  }

  public void addListener(ChangeListener listener) {
    dispatcher.addListener(listener);
  }

  public void removeListener(ChangeListener listener) {
    dispatcher.removeListener(listener);
  }

  void copyFrom(PreviewViewState other) {
  }
}
