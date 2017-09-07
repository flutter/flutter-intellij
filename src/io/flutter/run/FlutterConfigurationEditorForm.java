/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.jetbrains.lang.dart.ide.runner.server.ui.DartCommandLineConfigurationEditorForm.initDartFileTextWithBrowse;

public class FlutterConfigurationEditorForm extends SettingsEditor<SdkRunConfig> {
  private JPanel myMainPanel;
  private JLabel myDartFileLabel;
  private TextFieldWithBrowseButton myFileField;
  private JTextField myAdditionalArgumentsField;
  private JTextField myBuildFlavorField;

  public FlutterConfigurationEditorForm(final Project project) {
    initDartFileTextWithBrowse(project, myFileField);
  }

  @Override
  protected void resetEditorFrom(@NotNull final SdkRunConfig config) {
    final SdkFields fields = config.getFields();
    myFileField.setText(FileUtil.toSystemDependentName(StringUtil.notNullize(fields.getFilePath())));
    myBuildFlavorField.setText(fields.getBuildFlavor());
    myAdditionalArgumentsField.setText(fields.getAdditionalArgs());
  }

  @Override
  protected void applyEditorTo(@NotNull final SdkRunConfig config) throws ConfigurationException {
    final SdkFields fields = new SdkFields();
    fields.setFilePath(StringUtil.nullize(FileUtil.toSystemIndependentName(myFileField.getText().trim()), true));
    fields.setBuildFlavor(StringUtil.nullize(myBuildFlavorField.getText().trim()));
    fields.setAdditionalArgs(StringUtil.nullize(myAdditionalArgumentsField.getText().trim()));
    config.setFields(fields);
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return myMainPanel;
  }
}
