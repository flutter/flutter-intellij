/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBCheckBox;
import com.jetbrains.lang.dart.DartBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.jetbrains.lang.dart.ide.runner.server.ui.DartCommandLineConfigurationEditorForm.initDartFileTextWithBrowse;

public class FlutterConfigurationEditorForm extends SettingsEditor<FlutterRunConfiguration> {
  private JPanel myMainPanel;
  private JLabel myDartFileLabel;
  private TextFieldWithBrowseButton myFileField;
  private RawCommandLineEditor myVMOptions;
  private JBCheckBox myCheckedModeCheckBox;
  private RawCommandLineEditor myArguments;
  private TextFieldWithBrowseButton myWorkingDirectory;
  private EnvironmentVariablesComponent myEnvironmentVariables;
  private TextFieldWithBrowseButton myFlutterSdkPath;

  public FlutterConfigurationEditorForm(final Project project) {
    initDartFileTextWithBrowse(project, myFileField);
    //noinspection DialogTitleCapitalization
    myWorkingDirectory.addBrowseFolderListener(ExecutionBundle.message("select.working.directory.message"), null, project,
                                               FileChooserDescriptorFactory.createSingleFolderDescriptor());
    myVMOptions.setDialogCaption(DartBundle.message("config.vmoptions.caption"));
    myArguments.setDialogCaption(DartBundle.message("config.progargs.caption"));
    myDartFileLabel.setPreferredSize(myEnvironmentVariables.getLabel().getPreferredSize());
    myEnvironmentVariables.setAnchor(myDartFileLabel);
  }

  @Override
  protected void resetEditorFrom(@NotNull final FlutterRunConfiguration configuration) {
    final FlutterRunnerParameters parameters = configuration.getRunnerParameters();
    myFileField.setText(FileUtil.toSystemDependentName(StringUtil.notNullize(parameters.getFilePath())));
    myArguments.setText(StringUtil.notNullize(parameters.getArguments()));
    myVMOptions.setText(StringUtil.notNullize(parameters.getVMOptions()));
    myCheckedModeCheckBox.setSelected(parameters.isCheckedMode());
    myWorkingDirectory.setText(FileUtil.toSystemDependentName(StringUtil.notNullize(parameters.getWorkingDirectory())));
    myEnvironmentVariables.setEnvs(parameters.getEnvs());
    myEnvironmentVariables.setPassParentEnvs(parameters.isIncludeParentEnvs());
    myFlutterSdkPath.setText(FileUtil.toSystemDependentName(StringUtil.notNullize(parameters.getFlutterSdkPath())));
  }

  @Override
  protected void applyEditorTo(@NotNull final FlutterRunConfiguration configuration) throws ConfigurationException {
    final FlutterRunnerParameters parameters = configuration.getRunnerParameters();
    parameters.setFilePath(StringUtil.nullize(FileUtil.toSystemIndependentName(myFileField.getText().trim()), true));
    parameters.setArguments(StringUtil.nullize(myArguments.getText(), true));
    parameters.setVMOptions(StringUtil.nullize(myVMOptions.getText(), true));
    parameters.setCheckedMode(myCheckedModeCheckBox.isSelected());
    parameters.setWorkingDirectory(StringUtil.nullize(FileUtil.toSystemIndependentName(myWorkingDirectory.getText().trim()), true));
    parameters.setEnvs(myEnvironmentVariables.getEnvs());
    parameters.setIncludeParentEnvs(myEnvironmentVariables.isPassParentEnvs());
    parameters.setFlutterSdkPath(StringUtil.nullize(FileUtil.toSystemIndependentName(myFlutterSdkPath.getText().trim()), true));
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return myMainPanel;
  }
}
