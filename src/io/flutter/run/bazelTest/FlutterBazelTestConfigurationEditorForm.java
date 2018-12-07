/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ListCellRendererWrapper;
import com.jetbrains.lang.dart.ide.runner.server.ui.DartCommandLineConfigurationEditorForm;
import io.flutter.run.bazelTest.BazelTestFields.Scope;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import java.awt.event.ActionEvent;

import static io.flutter.run.bazelTest.BazelTestFields.Scope.FILE;
import static io.flutter.run.bazelTest.BazelTestFields.Scope.NAME;
import static io.flutter.run.bazelTest.BazelTestFields.Scope.TARGET_PATTERN;

public class FlutterBazelTestConfigurationEditorForm extends SettingsEditor<BazelTestConfig> {
  private JPanel myMainPanel;
  private JLabel scopeLabelHint;
  private JComboBox<Scope> scope;

  private JLabel myEntryFileLabel;
  private TextFieldWithBrowseButton myEntryFile;
  private JLabel myEntryFileHintLabel;

  private JLabel myLaunchingScriptLabel;
  private TextFieldWithBrowseButton myLaunchingScript;
  private JLabel myLaunchingScriptHintLabel;

  private JTextField myBuildTarget;
  private JLabel myBuildTargetHintLabel;
  private JLabel myBuildTargetLabel;

  private JLabel myTestNameLabel;
  private JTextField myTestName;
  private JLabel myTestNameHintLabel;

  private Scope displayedScope;

  public FlutterBazelTestConfigurationEditorForm(final Project project) {
    scope.setModel(new DefaultComboBoxModel<>(new Scope[]{TARGET_PATTERN, FILE, NAME}));
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
    final Scope next = fields.getScope();
    scope.setSelectedItem(next);
    render(next);
  }

  @Override
  protected void applyEditorTo(@NotNull final BazelTestConfig configuration) {
    final String testName = StringUtil.nullize(this.myTestName.getText().trim(), true);
    final String entryFile = StringUtil.nullize(FileUtil.toSystemIndependentName(myEntryFile.getText().trim()), true);
    final String launchScript = StringUtil.nullize(FileUtil.toSystemIndependentName(myLaunchingScript.getText().trim()), true);
    final String bazelTarget = StringUtil.nullize(myBuildTarget.getText().trim(), true);
    final BazelTestFields fields = new BazelTestFields(
      testName,
      entryFile,
      launchScript,
      bazelTarget
    );
    configuration.setFields(fields);
  }

  @NotNull
  @Override
  protected JComponent createEditor() {
    return myMainPanel;
  }


  /**
   * When switching between file and directory scope, update the next field to
   * a suitable default.
   */
  private void updateFields(Scope next) {
    if (next == Scope.TARGET_PATTERN && displayedScope != Scope.TARGET_PATTERN) {
    }
    else if (next != Scope.TARGET_PATTERN) {
      if (myEntryFile.getText().isEmpty()) {
        myEntryFile.setText(myBuildTarget.getText().replace("//", ""));
      }
    }
  }

  @NotNull
  private Scope getScope() {
    final Object item = scope.getSelectedItem();
    // Set in resetEditorForm.
    assert (item != null);
    return (Scope)item;
  }


  /**
   * Show and hide fields as appropriate for the next scope.
   */
  private void render(Scope next) {

    myEntryFile.setVisible(next == Scope.FILE || next == Scope.NAME);
    myEntryFileLabel.setVisible(next == Scope.FILE || next == Scope.NAME);
    myEntryFileHintLabel.setVisible(next == Scope.FILE || next == Scope.NAME);

    myTestName.setVisible(next == Scope.NAME);
    myTestNameLabel.setVisible(next == Scope.NAME);
    myTestNameHintLabel.setVisible(next == Scope.NAME);

    myBuildTarget.setVisible(next == Scope.TARGET_PATTERN);
    myBuildTargetLabel.setVisible(next == Scope.TARGET_PATTERN);
    myBuildTargetHintLabel.setVisible(next == Scope.TARGET_PATTERN);

    displayedScope = next;
  }
}
