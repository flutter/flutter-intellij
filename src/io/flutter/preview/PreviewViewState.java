/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.preview;

import com.intellij.util.EventDispatcher;
import com.intellij.util.xmlb.annotations.Attribute;
import io.flutter.FlutterInitializer;
import io.flutter.analytics.Analytics;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * State for the Preview view.
 */
public class PreviewViewState {
  private final EventDispatcher<ChangeListener> dispatcher = EventDispatcher.create(ChangeListener.class);

  @Attribute(value = "splitter-proportion")
  public float splitterProportion;

  @Attribute(value = "show-only-widgets")
  public boolean showOnlyWidgets = true;

  public PreviewViewState() {
  }

  public float getSplitterProportion() {
    return splitterProportion <= 0.0f ? 0.7f : splitterProportion;
  }

  public void setSplitterProportion(float value) {
    splitterProportion = value;
    dispatcher.getMulticaster().stateChanged(new ChangeEvent(this));
  }

  public boolean getShowOnlyWidgets() {
    return showOnlyWidgets;
  }

  public void setShowOnlyWidgets(boolean value) {
    showOnlyWidgets = value;
    dispatcher.getMulticaster().stateChanged(new ChangeEvent(this));
  }

  public void addListener(ChangeListener listener) {
    dispatcher.addListener(listener);
  }

  public void removeListener(ChangeListener listener) {
    dispatcher.removeListener(listener);
  }

  void copyFrom(PreviewViewState other) {
    splitterProportion = other.splitterProportion;
    showOnlyWidgets = other.showOnlyWidgets;

    sendToAnalytics();
  }

  private void sendToAnalytics() {
    final Analytics analytics = FlutterInitializer.getAnalytics();

    // Send the number of times the state is initialized.
    analytics.sendEvent("previewState", "ping");

    // Send boolean properties turned on.
    if (showOnlyWidgets) {
      analytics.sendEvent("previewState", "showOnlyWidgets");
    }
  }
}
