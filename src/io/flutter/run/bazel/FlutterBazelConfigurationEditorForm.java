/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazel;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.jetbrains.lang.dart.ide.runner.server.ui.DartCommandLineConfigurationEditorForm.initDartFileTextWithBrowse;

public class FlutterBazelConfigurationEditorForm extends SettingsEditor<BazelRunConfig> {
  private JPanel myMainPanel;

  private JTextField myBazelTarget;
  private JTextField myBazelArgs;
  private JTextField myAdditionalArgs;
  private JCheckBox myEnableReleaseModeCheckBox;
  private JCheckBox useDartFile;
  private TextFieldWithBrowseButton myDartTarget;
  private JLabel myBazelTargetText;
  private JLabel myDartTargetText;
  private JLabel myDartTargetLabel;
  private JLabel myBazelTargetLabel;

  public FlutterBazelConfigurationEditorForm(final Project project) {
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor();
    initDartFileTextWithBrowse(project, myDartTarget);
    addSettingsEditorListener(editor -> {
      if (myDartTarget.isVisible() != useDartFile.isSelected()) {
        chooseVisibleFields(useDartFile.isSelected());
      }
    });
    installWatcher(useDartFile);

    // Disable option to use a Dart file entrypoint until bazel run code is completed.
    useDartFile.setVisible(false);
  }

  @Override
  protected void resetEditorFrom(@NotNull final BazelRunConfig configuration) {
    final BazelFields fields = configuration.getFields();
    myBazelTarget.setText(StringUtil.notNullize(fields.getBazelTarget()));
    myDartTarget.setText(FileUtil.toSystemDependentName(StringUtil.notNullize(fields.getDartTarget())));
    myEnableReleaseModeCheckBox.setSelected(fields.getEnableReleaseMode());
    myBazelArgs.setText(StringUtil.notNullize(fields.getBazelArgs()));
    myAdditionalArgs.setText(StringUtil.notNullize(fields.getAdditionalArgs()));
    useDartFile.setSelected(fields.getUseDartTarget());
    chooseVisibleFields(fields.getUseDartTarget());
  }

  private void chooseVisibleFields(boolean useDartFile) {
    myDartTarget.setVisible(useDartFile);
    myDartTargetText.setVisible(useDartFile);
    myDartTargetLabel.setVisible(useDartFile);

    myBazelTarget.setVisible(!useDartFile);
    myBazelTargetText.setVisible(!useDartFile);
    myBazelTargetLabel.setVisible(!useDartFile);
  }

  @Override
  protected void applyEditorTo(@NotNull final BazelRunConfig configuration) throws ConfigurationException {
    final BazelFields fields = new BazelFields(
      getTextValue(myBazelTarget),
      StringUtil.nullize(FileUtil.toSystemIndependentName(myDartTarget.getText().trim()), true),
      getTextValue(myBazelArgs),
      getTextValue(myAdditionalArgs),
      myEnableReleaseModeCheckBox.isSelected(),
      useDartFile.isSelected()
    );
    configuration.setFields(fields);
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return myMainPanel;
  }

  @Nullable
  private String getTextValue(@NotNull JTextField textField) {
    return StringUtil.nullize(textField.getText().trim(), true);
  }
}
