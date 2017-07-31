/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module.settings;

import io.flutter.FlutterBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

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

  private JTextPane gettingStartedUrl;

  public SettingsHelpForm() {
    projectNameLabel.setText(FlutterBundle.message("flutter.module.create.settings.help.label"));

    projectNameLabel.setText(FlutterBundle.message("flutter.module.create.settings.help.project_name.label"));
    projectNameDescription.setText(FlutterBundle.message("flutter.module.create.settings.help.project_name.description"));

    projectTypeLabel.setText(FlutterBundle.message("flutter.module.create.settings.help.project_type.label"));
    projectTypeDescriptionForApp.setText(FlutterBundle.message("flutter.module.create.settings.help.project_type.description.app"));
    projectTypeDescriptionForPlugin.setText(FlutterBundle.message("flutter.module.create.settings.help.project_type.description.plugin"));

    orgLabel.setText(FlutterBundle.message("flutter.module.create.settings.help.org.label"));
    orgDescription.setText(FlutterBundle.message("flutter.module.create.settings.help.org.description"));

    gettingStartedUrl.setText(FlutterBundle.message("flutter.module.create.settings.help.getting_started_html"));
    gettingStartedUrl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    gettingStartedUrl.addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() > 0) {
          if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            try {
              URI uri = new URI("https://flutter.io/getting-started/");
              desktop.browse(uri);
            } catch (IOException ex) {
              // do nothing
            } catch (URISyntaxException ex) {
              //do nothing
            }
          } else {
            //do nothing
          }
        }
      }
    });
  }

  @NotNull
  public JComponent getComponent() {
    return mainPanel;
  }
}
