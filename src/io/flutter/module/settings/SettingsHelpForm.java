/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module.settings;

import io.flutter.FlutterBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

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

  public SettingsHelpForm() {
    projectNameLabel.setText(FlutterBundle.message("flutter.module.create.settings.help.label"));

    projectNameLabel.setText(FlutterBundle.message("flutter.module.create.settings.help.project_name.label"));
    projectNameDescription.setText(FlutterBundle.message("flutter.module.create.settings.help.project_name.description"));

    projectTypeLabel.setText(FlutterBundle.message("flutter.module.create.settings.help.project_type.label"));
    projectTypeDescriptionForApp.setText(FlutterBundle.message("flutter.module.create.settings.help.project_type.description.app"));
    projectTypeDescriptionForPlugin.setText(FlutterBundle.message("flutter.module.create.settings.help.project_type.description.plugin"));

    orgLabel.setText(FlutterBundle.message("flutter.module.create.settings.help.org.label"));
    orgDescription.setText(FlutterBundle.message("flutter.module.create.settings.help.org.description"));
  }

  @NotNull
  public JComponent getComponent() {
    return mainPanel;
  }
}
