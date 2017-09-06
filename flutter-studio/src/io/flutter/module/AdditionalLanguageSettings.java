/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.flutter.module;

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

  public AdditionalLanguageSettings() {
    ButtonGroup group = new ButtonGroup();
    group.add(myJavaRadioButton);
    group.add(myKotlinRadioButton);
    group = new ButtonGroup();
    group.add(myObjectiveCRadioButton);
    group.add(mySwiftRadioButton);
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
