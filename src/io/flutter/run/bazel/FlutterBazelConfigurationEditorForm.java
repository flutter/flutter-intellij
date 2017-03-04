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
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class FlutterBazelConfigurationEditorForm extends SettingsEditor<BazelRunConfig> {
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
  protected void resetEditorFrom(@NotNull final BazelRunConfig configuration) {
    final BazelFields fields = configuration.getFields();
    myWorkingDirectory.setText(FileUtil.toSystemDependentName(StringUtil.notNullize(fields.getWorkingDirectory())));
    myBuildTarget.setText(StringUtil.notNullize(fields.getBazelTarget()));
    myLaunchingScript.setText(FileUtil.toSystemDependentName(StringUtil.notNullize(fields.getLaunchingScript())));
    myAdditionalArgs.setText(StringUtil.notNullize(fields.getAdditionalArgs()));
  }

  @Override
  protected void applyEditorTo(@NotNull final BazelRunConfig configuration) throws ConfigurationException {
    final BazelFields fields = new BazelFields();
    fields.setWorkingDirectory(StringUtil.nullize(FileUtil.toSystemIndependentName(myWorkingDirectory.getText().trim()), true));
    fields.setBazelTarget(StringUtil.nullize(myBuildTarget.getText().trim(), true));
    fields.setLaunchingScript(StringUtil.nullize(FileUtil.toSystemIndependentName(myLaunchingScript.getText().trim()), true));
    fields.setAdditionalArgs(StringUtil.nullize(myAdditionalArgs.getText().trim(), true));
    configuration.setFields(fields);
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return myMainPanel;
  }
}
