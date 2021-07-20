/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.google.common.collect.Lists;
import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.Set;

import static com.jetbrains.lang.dart.ide.runner.server.ui.DartCommandLineConfigurationEditorForm.initDartFileTextWithBrowse;

public class FlutterConfigurationEditorForm extends SettingsEditor<SdkRunConfig> {
  private JPanel myMainPanel;
  private JLabel myDartFileLabel;
  private TextFieldWithBrowseButton myFileField;
  private JTextField myAdditionalArgumentsField;
  private JTextField myBuildFlavorField;
  private JTextField myAttachArgsField;
  private EnvironmentVariablesTextFieldWithBrowseButton myEnvironmentVariables;
  private JLabel myEnvvarLabel;

  private static final List<String> DESKTOP_PLATFORMS =
    Lists.newArrayList("enable-linux-desktop", "enable-macos-desktop", "enable-windows-desktop", "enable-windows-uwp-desktop");

  public FlutterConfigurationEditorForm(final Project project) {
    initDartFileTextWithBrowse(project, myFileField);
    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk != null) {
      final Set<String> platforms = sdk.queryConfiguredPlatforms(true);
      for (String platform : platforms) {
        if (DESKTOP_PLATFORMS.contains(platform)) {
          return;
        }
      }
    }
    myEnvironmentVariables.setEnabled(false);
    myEnvvarLabel.setEnabled(false);
  }

  @Override
  protected void resetEditorFrom(@NotNull final SdkRunConfig config) {
    final SdkFields fields = config.getFields();
    myFileField.setText(FileUtil.toSystemDependentName(StringUtil.notNullize(fields.getFilePath())));
    myBuildFlavorField.setText(fields.getBuildFlavor());
    myAdditionalArgumentsField.setText(fields.getAdditionalArgs());
    myAttachArgsField.setText(fields.getAttachArgs());
    myEnvironmentVariables.setEnvs(fields.getEnvs());
  }

  @Override
  protected void applyEditorTo(@NotNull final SdkRunConfig config) throws ConfigurationException {
    final SdkFields fields = new SdkFields();
    fields.setFilePath(StringUtil.nullize(FileUtil.toSystemIndependentName(myFileField.getText().trim()), true));
    fields.setBuildFlavor(StringUtil.nullize(myBuildFlavorField.getText().trim()));
    fields.setAdditionalArgs(StringUtil.nullize(myAdditionalArgumentsField.getText().trim()));
    fields.setAttachArgs(StringUtil.nullize(myAttachArgsField.getText().trim()));
    fields.setEnvs(myEnvironmentVariables.getEnvs());
    config.setFields(fields);
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return myMainPanel;
  }
}
