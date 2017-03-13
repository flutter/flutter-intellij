/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
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
  private TextFieldWithBrowseButton myWorkingDirectory;
  private JTextField myAdditionalArguments;

  public FlutterConfigurationEditorForm(final Project project) {
    initDartFileTextWithBrowse(project, myFileField);
    //noinspection DialogTitleCapitalization
    myWorkingDirectory.addBrowseFolderListener(ExecutionBundle.message("select.working.directory.message"), null, project,
                                               FileChooserDescriptorFactory.createSingleFolderDescriptor());
  }

  @Override
  protected void resetEditorFrom(@NotNull final SdkRunConfig config) {
    final SdkFields fields = config.getFields();
    myFileField.setText(FileUtil.toSystemDependentName(StringUtil.notNullize(fields.getFilePath())));
    myWorkingDirectory.setText(FileUtil.toSystemDependentName(StringUtil.notNullize(fields.getWorkingDirectory())));
    myAdditionalArguments.setText(fields.getAdditionalArgs());
  }

  @Override
  protected void applyEditorTo(@NotNull final SdkRunConfig config) throws ConfigurationException {
    final SdkFields fields = new SdkFields();
    fields.setFilePath(StringUtil.nullize(FileUtil.toSystemIndependentName(myFileField.getText().trim()), true));
    fields.setWorkingDirectory(StringUtil.nullize(FileUtil.toSystemIndependentName(myWorkingDirectory.getText().trim()), true));
    fields.setAdditionalArgs(StringUtil.nullize(myAdditionalArguments.getText().trim()));
    config.setFields(fields);
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return myMainPanel;
  }
}
