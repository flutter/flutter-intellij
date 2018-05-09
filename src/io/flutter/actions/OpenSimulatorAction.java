/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import io.flutter.FlutterMessages;
import io.flutter.sdk.XcodeUtils;

@SuppressWarnings("ComponentNotRegistered")
public class OpenSimulatorAction extends AnAction {
  final boolean enabled;

  public OpenSimulatorAction(boolean enabled) {
    super("Open iOS Simulator");

    this.enabled = enabled;
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(enabled);
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
      // Check to see if the simulator is already running.
      // If it is, and we're here, that means there are no booted devices.
      if (XcodeUtils.isSimulatorRunning()) {
        FlutterMessages.showDialog(event.getProject(),
                                   "It looks like you have the Simulator app open but no booted devices;\n"
                                   +"in the simulator, boot a device from the \"Hardware\" menu before running.", "No Booted Devices", new String[]{"OK"}, 0);
        return;
      }

      XcodeUtils.startSimulator();
  }
}
