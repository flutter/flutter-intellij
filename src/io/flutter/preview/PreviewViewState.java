/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import com.intellij.util.EventDispatcher;
import com.intellij.util.xmlb.annotations.Attribute;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * State for the Preview view.
 */
public class PreviewViewState {
  private final EventDispatcher<ChangeListener> dispatcher = EventDispatcher.create(ChangeListener.class);

  @Attribute(value = "splitter-proportion")
  public float splitterProportion;

  public PreviewViewState() {
  }

  public float getSplitterProportion() {
    return splitterProportion <= 0.0f ? 0.7f : splitterProportion;
  }

  public void setSplitterProportion(float value) {
    splitterProportion = value;
    dispatcher.getMulticaster().stateChanged(new ChangeEvent(this));
  }

  public void addListener(ChangeListener listener) {
    dispatcher.addListener(listener);
  }

  public void removeListener(ChangeListener listener) {
    dispatcher.removeListener(listener);
  }

  // This attribute exists only to silence the "com.intellij.util.xmlb.Binding - no accessors for class" warning.
  @Attribute(value = "placeholder")
  public String placeholder;

  void copyFrom(PreviewViewState other) {
    this.placeholder = other.placeholder;
    splitterProportion = other.splitterProportion;
  }
}
