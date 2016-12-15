/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazel;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import io.flutter.run.FlutterRunnerParameters;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class FlutterBazelConfigurationEditorForm extends SettingsEditor<FlutterBazelRunConfiguration> {
  private JPanel myMainPanel;

  private JLabel myWorkingDirectoryLabel;
  private TextFieldWithBrowseButton myWorkingDirectory;

  private JLabel myLaunchingScriptLabel;
  private TextFieldWithBrowseButton myLaunchingScript;
  private JTextField myAdditionalArgs;
  private JTextField myBuildTarget;

  public FlutterBazelConfigurationEditorForm(final Project project) {
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor();
    myLaunchingScript.addBrowseFolderListener("Select Launching Script", "Choose launching script", project, descriptor);

    //noinspection DialogTitleCapitalization
    this.myWorkingDirectory
      .addBrowseFolderListener(ExecutionBundle.message("select.working.directory.message"), null, project,
                               FileChooserDescriptorFactory.createSingleFolderDescriptor());
  }

  @Override
  protected void resetEditorFrom(@NotNull final FlutterBazelRunConfiguration configuration) {
    final FlutterRunnerParameters parameters = configuration.getRunnerParameters();
    myWorkingDirectory.setText(FileUtil.toSystemDependentName(StringUtil.notNullize(parameters.getWorkingDirectory())));
    myBuildTarget.setText(StringUtil.notNullize(parameters.getBazelTarget()));
    myLaunchingScript.setText(FileUtil.toSystemDependentName(StringUtil.notNullize(parameters.getLaunchingScript())));
    myAdditionalArgs.setText(StringUtil.notNullize(parameters.getAdditionalArgs()));
  }

  @Override
  protected void applyEditorTo(@NotNull final FlutterBazelRunConfiguration configuration) throws ConfigurationException {
    final FlutterRunnerParameters parameters = configuration.getRunnerParameters();
    parameters.setWorkingDirectory(StringUtil.nullize(FileUtil.toSystemIndependentName(myWorkingDirectory.getText().trim()), true));
    parameters.setBazelTarget(StringUtil.nullize(myBuildTarget.getText().trim(), true));
    parameters.setLaunchingScript(StringUtil.nullize(FileUtil.toSystemIndependentName(myLaunchingScript.getText().trim()), true));
    parameters.setAdditionalArgs(StringUtil.nullize(myAdditionalArgs.getText().trim(), true));
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return myMainPanel;
  }
}
