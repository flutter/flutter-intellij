/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module.settings;

import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.ui.components.labels.LinkLabel;
import io.flutter.FlutterBundle;
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

  private JLabel projectNameLabel;
  private JLabel projectNameDescription;

  private JLabel projectTypeLabel;
  private JLabel projectTypeDescriptionForApp;
  private JLabel projectTypeDescriptionForPlugin;

  private JLabel orgLabel;
  private JLabel orgDescription;

  private LinkLabel gettingStartedUrl;

  public SettingsHelpForm() {
    projectNameLabel.setText(FlutterBundle.message("flutter.module.create.settings.help.label"));

    projectNameLabel.setText(FlutterBundle.message("flutter.module.create.settings.help.project_name.label"));
    projectNameDescription.setText(FlutterBundle.message("flutter.module.create.settings.help.project_name.description"));

    projectTypeLabel.setText(FlutterBundle.message("flutter.module.create.settings.help.project_type.label"));
    projectTypeDescriptionForApp.setText(FlutterBundle.message("flutter.module.create.settings.help.project_type.description.app"));
    projectTypeDescriptionForPlugin.setText(FlutterBundle.message("flutter.module.create.settings.help.project_type.description.plugin"));

    orgLabel.setText(FlutterBundle.message("flutter.module.create.settings.help.org.label"));
    orgDescription.setText(FlutterBundle.message("flutter.module.create.settings.help.org.description"));

    gettingStartedUrl.setText(FlutterBundle.message("flutter.module.create.settings.help.getting_started_link_text"));
    gettingStartedUrl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    gettingStartedUrl.setIcon(null);
    //noinspection unchecked
    gettingStartedUrl
      .setListener((label, linkUrl) -> BrowserLauncher.getInstance().browse("https://flutter.io/getting-started/", null), null);
  }


  @NotNull
  public JComponent getComponent() {
    return mainPanel;
  }
}
