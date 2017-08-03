/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module.settings;

import com.intellij.ide.util.projectWizard.SettingsStep;
import io.flutter.FlutterBundle;
import io.flutter.sdk.FlutterCreateAdditionalSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class FlutterCreateAddtionalSettingsFields {

  private final JTextField orgField;
  private final JTextField descriptionField;
  private final RadiosForm androidLanguageRadios;
  private final RadiosForm iosLanguageRadios;
  private final ProjectType projectTypeForm;


  public FlutterCreateAddtionalSettingsFields() {
    projectTypeForm = new ProjectType();

    orgField = new JTextField();
    orgField.setText(FlutterBundle.message("flutter.module.create.settings.org.default_text"));
    orgField.setToolTipText(FlutterBundle.message("flutter.module.create.settings.org.tip"));

    descriptionField = new JTextField();
    descriptionField.setToolTipText(FlutterBundle.message("flutter.module.create.settings.description.tip"));
    descriptionField.setText(FlutterBundle.message("flutter.module.create.settings.description.default_text"));

    androidLanguageRadios = new RadiosForm(FlutterBundle.message("flutter.module.create.settings.radios.android.java"),
                                           FlutterBundle.message("flutter.module.create.settings.radios.android.kotlin"));
    androidLanguageRadios.setToolTipText(FlutterBundle.message("flutter.module.create.settings.radios.android.tip"));

    iosLanguageRadios = new RadiosForm(FlutterBundle.message("flutter.module.create.settings.radios.ios.object_c"),
                                       FlutterBundle.message("flutter.module.create.settings.radios.ios.swift"));
    iosLanguageRadios.setToolTipText(FlutterBundle.message("flutter.module.create.settings.radios.ios.tip"));
  }

  public void addSettingsFields(@NotNull SettingsStep settingsStep) {
    settingsStep.addSettingsField(FlutterBundle.message("flutter.module.create.settings.type.label"),
                                  projectTypeForm.getComponent());
    settingsStep.addSettingsField(FlutterBundle.message("flutter.module.create.settings.radios.org.label"), orgField);
    settingsStep.addSettingsField(FlutterBundle.message("flutter.module.create.settings.description.label"), descriptionField);
    settingsStep.addSettingsField(FlutterBundle.message("flutter.module.create.settings.radios.android.label"),
                                  androidLanguageRadios.getComponent());
    settingsStep.addSettingsField(FlutterBundle.message("flutter.module.create.settings.radios.ios.label"),
                                  iosLanguageRadios.getComponent());
    settingsStep.addSettingsComponent(new SettingsHelpForm().getComponent());
  }

  public FlutterCreateAdditionalSettings getAddtionalSettings() {
    return new FlutterCreateAdditionalSettings.Builder()
      .setDescription(!descriptionField.getText().trim().isEmpty() ? descriptionField.getText().trim() : null)
      .setGeneratePlugin(projectTypeForm.isPluginSelected() ? true : null)
      .setKotlin(androidLanguageRadios.isRadio2Selected() ? true : null)
      .setOrg(!orgField.getText().trim().isEmpty() ? orgField.getText().trim() : null)
      .setSwift(iosLanguageRadios.isRadio2Selected() ? true : null)
      .build();
  }
}
