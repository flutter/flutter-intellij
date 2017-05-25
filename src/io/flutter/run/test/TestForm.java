/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.test;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.jetbrains.lang.dart.ide.runner.server.ui.DartCommandLineConfigurationEditorForm.initDartFileTextWithBrowse;

/**
 * Settings editor for running Flutter tests.
 */
public class TestForm extends SettingsEditor<TestConfig> {
  private JPanel form;
  private TextFieldWithBrowseButton testFile;

  TestForm(@NotNull Project project) {
    initDartFileTextWithBrowse(project, testFile);
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return form;
  }

  @Override
  protected void resetEditorFrom(@NotNull TestConfig config) {
    final TestFields fields = config.getFields();
    testFile.setText(fields.getTestFile());
  }

  @Override
  protected void applyEditorTo(@NotNull TestConfig config) throws ConfigurationException {
    final TestFields fields = new TestFields(testFile.getText());
    config.setFields(fields);
  }
}
