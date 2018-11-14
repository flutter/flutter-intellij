/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module.settings;

import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.ui.components.labels.LinkLabel;
import io.flutter.FlutterBundle;
import io.flutter.FlutterConstants;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * The settings panel that list helps.
 */
public class SettingsHelpForm {
  private JPanel mainPanel;
  private JPanel helpPanel;

  private JLabel helpLabel;

  private JLabel projectTypeLabel;
  private JLabel projectTypeDescriptionForApp;
  private JLabel projectTypeDescriptionForPlugin;
  private JLabel projectTypeDescriptionForPackage;

  private LinkLabel gettingStartedUrl;

  public SettingsHelpForm() {
    projectTypeLabel.setText(FlutterBundle.message("flutter.module.create.settings.help.project_type.label"));
    projectTypeDescriptionForApp.setText(FlutterBundle.message("flutter.module.create.settings.help.project_type.description.app"));
    projectTypeDescriptionForPlugin.setText(FlutterBundle.message("flutter.module.create.settings.help.project_type.description.plugin"));
    projectTypeDescriptionForPackage.setText(FlutterBundle.message("flutter.module.create.settings.help.project_type.description.package"));

    gettingStartedUrl.setText(FlutterBundle.message("flutter.module.create.settings.help.getting_started_link_text"));
    gettingStartedUrl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    gettingStartedUrl.setIcon(null);
    //noinspection unchecked
    gettingStartedUrl
      .setListener((label, linkUrl) -> BrowserLauncher.getInstance().browse(FlutterConstants.URL_GETTING_STARTED, null), null);
  }


  @NotNull
  public JComponent getComponent() {
    return mainPanel;
  }
}
