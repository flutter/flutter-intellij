/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module.settings;

import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.openapi.ui.DialogPanel;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import io.flutter.FlutterBundle;
import io.flutter.FlutterUtils;
import io.flutter.module.FlutterProjectType;
import io.flutter.sdk.FlutterCreateAdditionalSettings;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.function.Supplier;

public class FlutterCreateAdditionalSettingsFields {
  private final FlutterCreateAdditionalSettings settings;
  private final JTextField orgField;
  private final JTextField descriptionField;
  private final RadiosForm androidLanguageRadios;
  private final ProjectType projectTypeForm;
  private final PlatformsForm platformsForm;
  private final FlutterCreateParams createParams;
  private SettingsHelpForm helpForm;
  @SuppressWarnings("FieldCanBeLocal")
  private DialogPanel panel;

  public FlutterCreateAdditionalSettingsFields(FlutterCreateAdditionalSettings additionalSettings,
                                               Supplier<? extends FlutterSdk> getSdk) {
    settings = additionalSettings;

    projectTypeForm = new ProjectType(getSdk);
    projectTypeForm.addListener(e -> {
      if (e.getStateChange() == ItemEvent.SELECTED) {
        FlutterProjectType type = projectTypeForm.getType();
        settings.setType(type);
        changeVisibility(type);
      }
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

    platformsForm = new PlatformsForm();
    createParams = new FlutterCreateParams();
  }

  private void changeVisibility(FlutterProjectType projectType) {
    boolean areLanguageFeaturesVisible = projectType != FlutterProjectType.PACKAGE && projectType != FlutterProjectType.MODULE;
    if (helpForm != null) {
      // Only in Android Studio.
      helpForm.adjustContrast(projectType);
    }
    orgField.setEnabled(projectType != FlutterProjectType.PACKAGE);
    UIUtil.setEnabled(androidLanguageRadios.getComponent(), areLanguageFeaturesVisible, true, true);
    UIUtil.setEnabled(platformsForm.getComponent(), areLanguageFeaturesVisible, true, true);
  }

  private void changeSettingsItemVisibility(JComponent component, boolean areLanguageFeaturesVisible) {
    // Note: This requires implementation knowledge of SettingsStep.addSettingsField(), which could change.
    if (component.getParent() == null) {
      return;
    }
    Component[] components = component.getParent().getComponents();
    int index;
    for (index = 0; index < components.length; index++) {
      if (components[index] == component) {
        break;
      }
    }
    Component label = components[index - 1];
    component.setVisible(areLanguageFeaturesVisible);
    label.setVisible(areLanguageFeaturesVisible);
  }

  public void addSettingsFields(@NotNull SettingsStep settingsStep) {
    settingsStep.addSettingsField(FlutterBundle.message("flutter.module.create.settings.description.label"), descriptionField);
    settingsStep.addSettingsField(FlutterBundle.message("flutter.module.create.settings.type.label"),
                                  projectTypeForm.getComponent());
    settingsStep.addSettingsField(FlutterBundle.message("flutter.module.create.settings.radios.org.label"), orgField);
    addBorder(androidLanguageRadios.getComponent(), false);
    settingsStep.addSettingsField(FlutterBundle.message("flutter.module.create.settings.radios.android.label"),
                                  androidLanguageRadios.getComponent());
    panel = platformsForm.panel(settings);
    addBorder(panel, true);
    settingsStep.addSettingsField(FlutterBundle.message("flutter.module.create.settings.platforms.label"), panel);


    if (!FlutterUtils.isAndroidStudio()) {
      // In IntelliJ the help form appears on the second page of the wizard, along with the project type selection drop-down.
      settingsStep.addSettingsComponent(new SettingsHelpForm().getComponent());
    }

    settingsStep.addSettingsComponent(createParams.setInitialValues().getComponent());
  }

  private void addBorder(JComponent c, boolean left) {
    // #addSettingsField() moves the second item up by 5.
    // We also add a bit to the left of the panel to make the checkbox line up with the radio button.
    c.setBorder(new AbstractBorder() {
      public Insets getBorderInsets(Component c, Insets insets) {
        return JBUI.insets(5, left ? 3 : 0, 0, 0);
      }
    });
  }

  public FlutterCreateAdditionalSettings getAdditionalSettings() {
    return new FlutterCreateAdditionalSettings.Builder()
      .setDescription(!descriptionField.getText().trim().isEmpty() ? descriptionField.getText().trim() : null)
      .setType(projectTypeForm.getType())
      // Packages are pure Dart code, no iOS or Android modules.
      .setKotlin(androidLanguageRadios.isRadio2Selected() ? true : null)
      .setOrg(!orgField.getText().trim().isEmpty() ? orgField.getText().trim() : null)
      .setOffline(createParams.isOfflineSelected())
      .setPlatformAndroid(shouldIncludePlatforms() ? settings.getPlatformAndroid() : null)
      .setPlatformIos(shouldIncludePlatforms() ? settings.getPlatformIos() : null)
      .setPlatformLinux(shouldIncludePlatforms() ? settings.getPlatformLinux() : null)
      .setPlatformMacos(shouldIncludePlatforms() ? settings.getPlatformMacos() : null)
      .setPlatformWeb(
        shouldIncludePlatforms() && projectTypeForm.getType() != FlutterProjectType.PLUGIN_FFI ? settings.getPlatformWeb() : null)
      .setPlatformWindows(shouldIncludePlatforms() ? settings.getPlatformWindows() : null)
      .build();
  }

  public boolean shouldIncludePlatforms() {
    return switch (projectTypeForm.getType()) { // fall through
      case APP, SKELETON, PLUGIN, PLUGIN_FFI, EMPTY_PROJECT -> true;
      default -> false;
    };
  }

  public FlutterCreateAdditionalSettings getSettings() {
    return settings;
  }

  // This is used in Android Studio to emphasize the help text for the selected project type.
  // The help form appears on the first page of the wizard.
  public void linkHelpForm(SettingsHelpForm form) {
    helpForm = form;
  }

  public void updateProjectTypes() {
    projectTypeForm.updateProjectTypes();
  }
}
