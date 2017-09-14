/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import io.flutter.FlutterBundle;
import io.flutter.sdk.FlutterCreateAdditionalSettings;

import javax.swing.*;
import java.awt.*;

public class AdditionalLanguageSettings {
  private JPanel myPanel;
  private JTextField myOrganization;
  private JRadioButton myJavaRadioButton;
  private JRadioButton myKotlinRadioButton;
  private JRadioButton myObjectiveCRadioButton;
  private JRadioButton mySwiftRadioButton;
  private JLabel myAndroidLanguageLabel;
  private JLabel myOrgDescription;

  public AdditionalLanguageSettings() {
    ButtonGroup group = new ButtonGroup();
    group.add(myJavaRadioButton);
    group.add(myKotlinRadioButton);
    group = new ButtonGroup();
    group.add(myObjectiveCRadioButton);
    group.add(mySwiftRadioButton);
    myOrgDescription.setText(FlutterBundle.message("flutter.module.create.settings.help.org.description"));
  }

  public JTextField getOrganizationField() {
    return myOrganization;
  }

  public JRadioButton getJavaRadioButton() {
    return myJavaRadioButton;
  }

  public JRadioButton getKotlinRadioButton() {
    return myKotlinRadioButton;
  }

  public JRadioButton getObjectiveCRadioButton() {
    return myObjectiveCRadioButton;
  }

  public JRadioButton getSwiftRadioButton() {
    return mySwiftRadioButton;
  }

  public JPanel getComponent() {
    return myPanel;
  }

  public Dimension getLabelColumnSize() {
    return myAndroidLanguageLabel.getPreferredSize();
  }

  public void setInitialValues(FlutterCreateAdditionalSettings settings) {
    myOrganization.setText(settings.getOrg());
    myJavaRadioButton.setSelected(true);
    myObjectiveCRadioButton.setSelected(true);
  }
}
