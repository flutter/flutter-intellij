/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.test;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.ListCellRendererWrapper;
import io.flutter.run.test.TestFields.Scope;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import java.awt.event.ActionEvent;

import static com.jetbrains.lang.dart.ide.runner.server.ui.DartCommandLineConfigurationEditorForm.initDartFileTextWithBrowse;
import static io.flutter.run.test.TestFields.Scope.DIRECTORY;
import static io.flutter.run.test.TestFields.Scope.FILE;

/**
 * Settings editor for running Flutter tests.
 */
public class TestForm extends SettingsEditor<TestConfig> {
  private JPanel form;

  private JComboBox<Scope> scope;

  private JLabel testDirLabel;
  private TextFieldWithBrowseButton testDir;

  private JLabel testFileLabel;
  private TextFieldWithBrowseButton testFile;

  TestForm(@NotNull Project project) {
    scope.setModel(new DefaultComboBoxModel<>(new Scope[]{DIRECTORY, FILE}));
    scope.addActionListener((ActionEvent e) -> render());
    scope.setRenderer(new ListCellRendererWrapper<Scope>() {
      @Override
      public void customize(final JList list,
                            final Scope value,
                            final int index,
                            final boolean selected,
                            final boolean hasFocus) {
        setText(value.getDisplayName());
      }
    });

    initDartFileTextWithBrowse(project, testFile);
    testDir.addBrowseFolderListener("Test Directory", null, project,
                                    FileChooserDescriptorFactory.createSingleFolderDescriptor());
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return form;
  }

  @Override
  protected void resetEditorFrom(@NotNull TestConfig config) {
    final TestFields fields = config.getFields();
    scope.setSelectedItem(fields.getScope());
    switch (fields.getScope()) {
      case FILE:
        testFile.setText(fields.getTestFile());
        break;
      case DIRECTORY:
        testDir.setText(fields.getTestDir());
        break;
    }
    render();
  }

  @Override
  protected void applyEditorTo(@NotNull TestConfig config) throws ConfigurationException {
    final TestFields fields;
    switch (getScope()) {
      case FILE:
        fields = TestFields.forFile(testFile.getText());
        break;
      case DIRECTORY:
        fields = TestFields.forDir(testDir.getText());
        break;
      default:
        throw new ConfigurationException("unexpected scope: " + scope.getSelectedItem());
    }
    config.setFields(fields);
  }

  @NotNull
  private Scope getScope() {
    return (Scope)scope.getSelectedItem();
  }

  private void render() {
    testDirLabel.setVisible(getScope() == Scope.DIRECTORY);
    testDir.setVisible(getScope() == Scope.DIRECTORY);

    testFileLabel.setVisible(getScope() == Scope.FILE);
    testFile.setVisible(getScope() == Scope.FILE);
  }
}
