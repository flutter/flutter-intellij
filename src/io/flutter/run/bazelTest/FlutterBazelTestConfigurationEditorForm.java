/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.lang.dart.ide.runner.server.ui.DartCommandLineConfigurationEditorForm;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class FlutterBazelTestConfigurationEditorForm extends SettingsEditor<BazelTestConfig> {
  private JPanel myMainPanel;

  private JLabel myEntryFileLabel;
  private TextFieldWithBrowseButton myEntryFile;

  private JLabel myLaunchingScriptLabel;
  private TextFieldWithBrowseButton myLaunchingScript;
  //private JTextField myAdditionalArgs;
  private JTextField myBuildTarget;

  public FlutterBazelTestConfigurationEditorForm(final Project project) {
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor();
    myLaunchingScript.addBrowseFolderListener("Select Launching Script", "Choose launching script", project, descriptor);

    DartCommandLineConfigurationEditorForm.initDartFileTextWithBrowse(project, myEntryFile);
  }

  @Override
  protected void resetEditorFrom(@NotNull final BazelTestConfig configuration) {
    final BazelTestFields fields = configuration.getFields();
    myEntryFile.setText(FileUtil.toSystemDependentName(StringUtil.notNullize(fields.getEntryFile())));
    myBuildTarget.setText(StringUtil.notNullize(fields.getBazelTarget()));
    myLaunchingScript.setText(FileUtil.toSystemDependentName(StringUtil.notNullize(fields.getLaunchingScript())));
    //myAdditionalArgs.setText(StringUtil.notNullize(fields.getAdditionalArgs()));
  }

  @Override
  protected void applyEditorTo(@NotNull final BazelTestConfig configuration) throws ConfigurationException {
    final BazelTestFields fields = new BazelTestFields();
    fields.setEntryFile(StringUtil.nullize(FileUtil.toSystemIndependentName(myEntryFile.getText().trim()), true));
    fields.setBazelTarget(StringUtil.nullize(myBuildTarget.getText().trim(), true));
    fields.setLaunchingScript(StringUtil.nullize(FileUtil.toSystemIndependentName(myLaunchingScript.getText().trim()), true));
    //fields.setAdditionalArgs(StringUtil.nullize(myAdditionalArgs.getText().trim(), true));
    configuration.setFields(fields);
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return myMainPanel;
  }
}
