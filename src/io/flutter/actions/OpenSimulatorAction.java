/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import io.flutter.sdk.XcodeUtils;
import org.jetbrains.annotations.NotNull;

public class OpenSimulatorAction extends AnAction {
  final boolean enabled;

  public OpenSimulatorAction(boolean enabled) {
    //noinspection DialogTitleCapitalization
    super("Open iOS Simulator");

    this.enabled = enabled;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(enabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    // Check to see if the simulator is already running. If it is, and we're here, that means there are
    // no running devices and we want to issue an extra call to start (w/ `-n`) to load a new simulator.
    // TODO(devoncarew): Determine if we need to support this code path.
    //if (XcodeUtils.isSimulatorRunning()) {
    //  if (XcodeUtils.openSimulator("-n") != 0) {
    //    // No point in trying if we errored.
    //    return;
    //  }
    //}

    XcodeUtils.openSimulator();
  }
}
