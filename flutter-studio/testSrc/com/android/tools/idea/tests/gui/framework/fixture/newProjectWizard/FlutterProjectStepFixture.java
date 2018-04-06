/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard;

import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardStepFixture;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import io.flutter.project.FlutterProjectStep;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.fixture.JComboBoxFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.io.File;

import static com.google.common.truth.Truth.assertThat;
import static org.fest.swing.edt.GuiActionRunner.execute;

// TODO(messick): Browse button for SDK; "Install SDK" button
public class FlutterProjectStepFixture<W extends AbstractWizardFixture> extends AbstractWizardStepFixture<FlutterProjectStepFixture, W> {
  protected FlutterProjectStepFixture(@NotNull W wizard, @NotNull JRootPane target) {
    super(FlutterProjectStepFixture.class, wizard, target);
  }

  private static boolean isShown(JComponent field) {
    return field.isVisible() && field.isShowing();
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
    //comboBox.replaceText(text);
    FlutterProjectStep.ensureComboModelContainsCurrentItem(comboBox.target());
    return this;
  }

  @NotNull
  public FlutterProjectStepFixture enterProjectLocation(@NotNull String text) {
    final TextFieldWithBrowseButton locationField = getLocationField();
    replaceText(locationField.getTextField(), text);
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
    final TextFieldWithBrowseButton locationField = getLocationField();
    return execute(new GuiQuery<File>() {
      @Override
      protected File executeInEDT() throws Throwable {
        String location = locationField.getText();
        assertThat(location).isNotEmpty();
        return new File(location);
      }
    });
  }

  @Nullable
  public String getErrorMessage() {
    Component comp = robot().finder().findByName("ValidationLabel");
    if (comp instanceof JBLabel) {
      JBLabel label = (JBLabel)comp;
      return label.getText();
    }
    return null;
  }

  @NotNull
  protected JComboBoxFixture findComboBox() {
    JComboBox comboBox = robot().finder().findByType(target(), JComboBox.class, true);
    return new JComboBoxFixture(robot(), comboBox);
  }

  public boolean isConfiguredForModules() {
    try {
      return isShown(findTextFieldWithLabel("Project name")) &&
             isShown(findTextFieldWithLabel("Description")) &&
             isShown(findComboBox().target()) &&
             !isShown(getLocationField());
    }
    catch (ComponentLookupException ex) {
      // Expect this exception when the location field is not found.
      return true;
    }
  }

  @NotNull
  private TextFieldWithBrowseButton getLocationField() {
    // This works for the project wizard. It might not work for the module wizard, if it were needed
    // because several Android modules use TextFieldWithBrowseButton. Fortunately, we expect it to
    // not be found in the module wizard because none are showing.
    return robot().finder().findByType(target(), TextFieldWithBrowseButton.class);
  }
}
