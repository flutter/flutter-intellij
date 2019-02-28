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
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FlutterBazelConfigurationEditorForm extends SettingsEditor<BazelRunConfig> {
  private JPanel myMainPanel;

  private JTextField myBazelTarget;
  private JTextField myBazelArgs;
  private JTextField myAdditionalArgs;
  private JCheckBox myEnableReleaseModeCheckBox;

  public FlutterBazelConfigurationEditorForm(final Project project) {
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor();
  }

  @Override
  protected void resetEditorFrom(@NotNull final BazelRunConfig configuration) {
    final BazelFields fields = configuration.getFields();
    myBazelTarget.setText(StringUtil.notNullize(fields.getBazelTarget()));
    myEnableReleaseModeCheckBox.setSelected(fields.getEnableReleaseMode());
    myBazelArgs.setText(StringUtil.notNullize(fields.getBazelArgs()));
    myAdditionalArgs.setText(StringUtil.notNullize(fields.getAdditionalArgs()));
  }

  @Override
  protected void applyEditorTo(@NotNull final BazelRunConfig configuration) throws ConfigurationException {
    final BazelFields fields = new BazelFields(
      getTextValue(myBazelTarget),
      getTextValue(myBazelArgs),
      getTextValue(myAdditionalArgs),
      myEnableReleaseModeCheckBox.isSelected()
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
