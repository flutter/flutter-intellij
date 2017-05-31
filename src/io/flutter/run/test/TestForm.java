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
import java.io.File;

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

  private Scope displayedScope;

  TestForm(@NotNull Project project) {
    scope.setModel(new DefaultComboBoxModel<>(new Scope[]{DIRECTORY, FILE}));
    scope.addActionListener((ActionEvent e) -> {
      final Scope next = getScope();
      updateFields(next);
      render(next);
    });
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
    final Scope next = fields.getScope();
    scope.setSelectedItem(next);
    switch (next) {
      case FILE:
        testFile.setText(fields.getTestFile());
        break;
      case DIRECTORY:
        testDir.setText(fields.getTestDir());
        break;
    }
    render(next);
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

  /**
   * When switching between file and directory scope, update the next field to
   * a suitable default.
   */
  private void updateFields(Scope next) {
    if (next == Scope.DIRECTORY && displayedScope == Scope.FILE) {
      final String sep = String.valueOf(File.separatorChar);

      final String path = testFile.getText();
      if (path.contains(sep) && path.endsWith(".dart")) {
        // Remove the last part of the path to get a directory.
        testDir.setText(path.substring(0, path.lastIndexOf(sep) + 1));
      } else if (testDir.getText().isEmpty()) {
        // Keep the same path; better than starting blank.
        testDir.setText(path);
      }

    } else if (next == Scope.FILE && displayedScope == Scope.DIRECTORY) {
      if (testFile.getText().isEmpty()) {
        testFile.setText(testDir.getText());
      }
    }
  }

  /**
   * Show and hide fields as appropriate for the next scope.
   */
  private void render(Scope next) {

    testDirLabel.setVisible(next == Scope.DIRECTORY);
    testDir.setVisible(next == Scope.DIRECTORY);

    testFileLabel.setVisible(next == Scope.FILE);
    testFile.setVisible(next == Scope.FILE);

    displayedScope = next;
  }
}
