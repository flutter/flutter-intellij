/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import io.flutter.FlutterInitializer;
import org.jetbrains.annotations.NotNull;

/**
 * An action that sends analytics when performed.
 */
public abstract class ActionWithAnalytics extends AnAction {

  /**
   * Template method.  Implement @performAction.
   */
  @Override
  public final void actionPerformed(AnActionEvent e) {
    FlutterInitializer.getAnalytics().sendEvent("flutter", getAnalyticsId());
    performAction(e);
  }

  /**
   * Return the unique analytics Id for this action.
   */
  @NotNull
  public abstract String getAnalyticsId();

  /**
   * Perform the action.
   *
   * Called by @actionPerformed.
   */
  public abstract void performAction(AnActionEvent e);
}
