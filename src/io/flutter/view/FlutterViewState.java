/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.util.EventDispatcher;
import com.intellij.util.xmlb.annotations.Attribute;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * State for the Flutter view.
 */
public class FlutterViewState {
  public static final boolean AUTO_SCROLL_DEFAULT = false;

  private final EventDispatcher<ChangeListener> dispatcher = EventDispatcher.create(ChangeListener.class);

  @Attribute(value = "splitter-proportion")
  public float splitterProportion;

  @Attribute(value = "should-auto-scroll")
  public boolean shouldAutoScroll = AUTO_SCROLL_DEFAULT;

  public FlutterViewState() {
  }

  public float getSplitterProportion() {
    return splitterProportion <= 0.0f ? 0.8f : splitterProportion;
  }

  public void setSplitterProportion(float value) {
    splitterProportion = value;
    dispatcher.getMulticaster().stateChanged(new ChangeEvent(this));
  }

  public boolean getShouldAutoScroll() {
    return shouldAutoScroll;
  }

  public void setShouldAutoScroll(boolean value) {
    shouldAutoScroll = value;
    dispatcher.getMulticaster().stateChanged(new ChangeEvent(this));
  }

  public void addListener(ChangeListener listener) {
    dispatcher.addListener(listener);
  }

  public void removeListener(ChangeListener listener) {
    dispatcher.removeListener(listener);
  }

  void copyFrom(FlutterViewState other) {
    splitterProportion = other.splitterProportion;
  }
}
