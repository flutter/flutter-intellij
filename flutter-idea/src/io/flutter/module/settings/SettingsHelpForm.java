/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module.settings;

import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.ui.components.labels.LinkLabel;
import io.flutter.FlutterBundle;
import io.flutter.FlutterConstants;
import io.flutter.FlutterUtils;
import io.flutter.module.FlutterProjectType;
import java.awt.Cursor;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * The settings panel that lists help messages.
 */
public class SettingsHelpForm {
  private JPanel mainPanel;
  private JPanel helpPanel;

  private JLabel helpLabel;

  private JLabel projectTypeLabel;
  private JLabel projectTypeDescriptionForApp;
  private JLabel projectTypeDescriptionForModule;
  private JLabel projectTypeDescriptionForPlugin;
  private JLabel projectTypeDescriptionForPackage;

  @SuppressWarnings("rawtypes")
  private LinkLabel gettingStartedUrl;

  public static SettingsHelpForm getInstance() {
    return ServiceManager.getService(SettingsHelpForm.class);
  }

  public SettingsHelpForm() {
    projectTypeLabel.setText(FlutterBundle.message("flutter.module.create.settings.help.project_type.label"));
    projectTypeDescriptionForApp.setText(FlutterBundle.message("flutter.module.create.settings.help.project_type.description.app"));
    projectTypeDescriptionForModule.setText(FlutterBundle.message("flutter.module.create.settings.help.project_type.description.module"));
    projectTypeDescriptionForPlugin.setText(FlutterBundle.message("flutter.module.create.settings.help.project_type.description.plugin"));
    projectTypeDescriptionForPackage.setText(FlutterBundle.message("flutter.module.create.settings.help.project_type.description.package"));

    if (!FlutterUtils.isAndroidStudio()) {
      projectTypeDescriptionForModule.setVisible(false);
    }

    gettingStartedUrl.setText(FlutterBundle.message("flutter.module.create.settings.help.getting_started_link_text"));
    gettingStartedUrl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    gettingStartedUrl.setIcon(null);
    //noinspection unchecked
    gettingStartedUrl
      .setListener((label, linkUrl) -> BrowserLauncher.getInstance().browse(FlutterConstants.URL_GETTING_STARTED, null), null);
  }

  public void showGettingStarted() {
    projectTypeLabel.setVisible(false);
    projectTypeDescriptionForApp.setVisible(false);
    projectTypeDescriptionForModule.setVisible(false);
    projectTypeDescriptionForPlugin.setVisible(false);
    projectTypeDescriptionForPackage.setVisible(false);
    mainPanel.setVisible(true);
    gettingStartedUrl.setVisible(true);
  }

  @NotNull
  public JComponent getComponent() {
    return mainPanel;
  }

  public void adjustContrast(FlutterProjectType type) {
    projectTypeDescriptionForApp.setEnabled(false);
    projectTypeDescriptionForModule.setEnabled(false);
    projectTypeDescriptionForPlugin.setEnabled(false);
    projectTypeDescriptionForPackage.setEnabled(false);
    switch (type) {
      case APP:
        projectTypeDescriptionForApp.setEnabled(true);
        break;
      case MODULE:
        projectTypeDescriptionForModule.setEnabled(true);
        break;
      case PACKAGE:
        projectTypeDescriptionForPackage.setEnabled(true);
        break;
      case PLUGIN:
        projectTypeDescriptionForPlugin.setEnabled(true);
        break;
      default:
        break;
    }
  }
}
