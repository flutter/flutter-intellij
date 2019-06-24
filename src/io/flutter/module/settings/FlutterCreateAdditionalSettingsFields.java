/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module.settings;

import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ui.UIUtil;
import io.flutter.FlutterBundle;
import io.flutter.module.FlutterProjectType;
import io.flutter.sdk.FlutterCreateAdditionalSettings;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ItemEvent;

public class FlutterCreateAdditionalSettingsFields {
  private final FlutterCreateAdditionalSettings settings;
  private final JTextField orgField;
  private final JTextField descriptionField;
  private final RadiosForm androidLanguageRadios;
  private final RadiosForm iosLanguageRadios;
  private final ProjectType projectTypeForm;
  private final FlutterCreateParams createParams;

  public FlutterCreateAdditionalSettingsFields() {
    this(new FlutterCreateAdditionalSettings(), null);
  }

  public FlutterCreateAdditionalSettingsFields(FlutterCreateAdditionalSettings additionalSettings, FlutterSdk sdk) {
    settings = additionalSettings;

    projectTypeForm = new ProjectType(sdk);
    projectTypeForm.addListener(e -> {
      settings.setType(projectTypeForm.getType());
      changeVisibility(projectTypeForm.getType() != FlutterProjectType.PACKAGE);
    });

    orgField = new JTextField();
    orgField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        settings.setOrg(orgField.getText());
      }
    });
    orgField.setText(FlutterBundle.message("flutter.module.create.settings.org.default_text"));
    orgField.setToolTipText(FlutterBundle.message("flutter.module.create.settings.org.tip"));

    descriptionField = new JTextField();
    descriptionField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        settings.setDescription(descriptionField.getText());
      }
    });
    descriptionField.setToolTipText(FlutterBundle.message("flutter.module.create.settings.description.tip"));
    descriptionField.setText(FlutterBundle.message("flutter.module.create.settings.description.default_text"));

    androidLanguageRadios = new RadiosForm(FlutterBundle.message("flutter.module.create.settings.radios.android.java"),
                                           FlutterBundle.message("flutter.module.create.settings.radios.android.kotlin"));
    androidLanguageRadios.addItemListener(
      e -> {
        final boolean isJavaSelected = e.getStateChange() == ItemEvent.SELECTED;
        settings.setKotlin(!isJavaSelected);
      }
    );
    androidLanguageRadios.setToolTipText(FlutterBundle.message("flutter.module.create.settings.radios.android.tip"));

    iosLanguageRadios = new RadiosForm(FlutterBundle.message("flutter.module.create.settings.radios.ios.object_c"),
                                       FlutterBundle.message("flutter.module.create.settings.radios.ios.swift"));
    androidLanguageRadios.addItemListener(
      e -> {
        final boolean isObjcSelected = e.getStateChange() == ItemEvent.SELECTED;
        settings.setSwift(!isObjcSelected);
      }
    );
    iosLanguageRadios.setToolTipText(FlutterBundle.message("flutter.module.create.settings.radios.ios.tip"));

    createParams = new FlutterCreateParams();
  }

  private void changeVisibility(boolean areLanguageFeaturesVisible) {
    orgField.setEnabled(areLanguageFeaturesVisible);
    UIUtil.setEnabled(androidLanguageRadios.getComponent(), areLanguageFeaturesVisible, true, true);
    UIUtil.setEnabled(iosLanguageRadios.getComponent(), areLanguageFeaturesVisible, true, true);
  }

  public void addSettingsFields(@NotNull SettingsStep settingsStep) {
    settingsStep.addSettingsField(FlutterBundle.message("flutter.module.create.settings.description.label"), descriptionField);
    settingsStep.addSettingsField(FlutterBundle.message("flutter.module.create.settings.type.label"),
                                  projectTypeForm.getComponent());
    settingsStep.addSettingsField(FlutterBundle.message("flutter.module.create.settings.radios.org.label"), orgField);
    settingsStep.addSettingsField(FlutterBundle.message("flutter.module.create.settings.radios.android.label"),
                                  androidLanguageRadios.getComponent());
    settingsStep.addSettingsField(FlutterBundle.message("flutter.module.create.settings.radios.ios.label"),
                                  iosLanguageRadios.getComponent());
    // WebStorm has a smaller area for the wizard UI.
    if (!PlatformUtils.isWebStorm()) {
      settingsStep.addSettingsComponent(new SettingsHelpForm().getComponent());
    }

    settingsStep.addSettingsComponent(createParams.setInitialValues().getComponent());
  }

  public FlutterCreateAdditionalSettings getAdditionalSettings() {
    return new FlutterCreateAdditionalSettings.Builder()
      .setDescription(!descriptionField.getText().trim().isEmpty() ? descriptionField.getText().trim() : null)
      .setType(projectTypeForm.getType())
      .setKotlin(androidLanguageRadios.isRadio2Selected() ? true : null)
      .setOrg(!orgField.getText().trim().isEmpty() ? orgField.getText().trim() : null)
      .setSwift(iosLanguageRadios.isRadio2Selected() ? true : null)
      .setOffline(createParams.isOfflineSelected())
      .build();
  }

  public FlutterCreateAdditionalSettings getSettings() {
    return settings;
  }
}
