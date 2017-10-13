/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import io.flutter.project.FlutterProjectStep;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.JCheckBoxFixture;
import org.fest.swing.fixture.JComboBoxFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.io.File;

import static com.google.common.truth.Truth.assertThat;
import static org.fest.swing.edt.GuiActionRunner.execute;

// TODO(messick): Browse button for SDK; "Install SDK" button
public class FlutterProjectStepFixture extends AbstractWizardStepFixture<FlutterProjectStepFixture> {
  protected FlutterProjectStepFixture(@NotNull Robot robot, @NotNull JRootPane target) {
    super(FlutterProjectStepFixture.class, robot, target);
  }

  @NotNull
  public FlutterProjectStepFixture enterProjectName(@NotNull String text) {
    JTextComponent textField = findTextFieldWithLabel("Project name");
    replaceText(textField, text);
    return this;
  }

  @NotNull
  public FlutterProjectStepFixture enterSdkPath(@NotNull String text) {
    JComboBoxFixture comboBox = findComboBox();
    comboBox.replaceText(text);
    FlutterProjectStep.ensureComboModelContainsCurrentItem(comboBox.target());
    return this;
  }

  @NotNull
  public FlutterProjectStepFixture enterProjectLocation(@NotNull String text) {
    JTextComponent textField = findTextFieldWithLabel("Project location");
    replaceText(textField, text);
    return this;
  }

  @NotNull
  public FlutterProjectStepFixture enterDescription(@NotNull String text) {
    JTextComponent textField = findTextFieldWithLabel("Description");
    replaceText(textField, text);
    return this;
  }

  public String getProjectName() {
    return findTextFieldWithLabel("Project name").getText();
  }

  public String getSdkPath() {
    return findComboBox().selectedItem();
  }

  public String getProjectLocation() {
    return getLocationInFileSystem().getPath();
  }

  public String getDescription() {
    return findTextFieldWithLabel("Description").getText();
  }

  @NotNull
  public File getLocationInFileSystem() {
    final TextFieldWithBrowseButton locationField = robot().finder().findByType(target(), TextFieldWithBrowseButton.class);
    return execute(new GuiQuery<File>() {
      @Override
      protected File executeInEDT() throws Throwable {
        String location = locationField.getText();
        assertThat(location).isNotEmpty();
        return new File(location);
      }
    });
  }

  @NotNull
  protected JComboBoxFixture findComboBox() {
    JComboBox comboBox = robot().finder().findByType(target(), JComboBox.class, true);
    return new JComboBoxFixture(robot(), comboBox);
  }
}
