/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module.settings;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public class FlutterCreateParams {
  private JCheckBox createProjectOfflineCheckBox;
  private JPanel mainPanel;
  private JLabel infoLabel;

  public FlutterCreateParams setInitialValues() {
    final boolean autoSelectOffline = !isPubAvailable();
    createProjectOfflineCheckBox.setSelected(autoSelectOffline);
    infoLabel.setVisible(autoSelectOffline);
    return this;
  }

  @NotNull
  public JComponent getComponent() {
    return mainPanel;
  }

  public boolean isOfflineSelected() {
    return createProjectOfflineCheckBox.isSelected();
  }

  public JCheckBox getOfflineCheckbox() {
    return createProjectOfflineCheckBox;
  }

  private static boolean isPubAvailable() {
    // Check to see if the pub site is accessible to indicate whether we're online
    // and if we should expect pub commands to succeed.
    try {
      new Socket(InetAddress.getByName("pub.dartlang.org"), 80).close();
      return true;
    }
    catch (IOException ex) {
      // Ignore.
    }
    return false;
  }
}
