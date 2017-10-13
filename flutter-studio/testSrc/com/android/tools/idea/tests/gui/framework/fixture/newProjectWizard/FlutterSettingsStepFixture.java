/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard;

import com.android.tools.adtui.LabelWithEditButton;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import io.flutter.FlutterBundle;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.JCheckBoxFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.io.File;

import static com.google.common.truth.Truth.assertThat;
import static org.fest.swing.edt.GuiActionRunner.execute;

public class FlutterSettingsStepFixture extends AbstractWizardStepFixture<FlutterSettingsStepFixture> {
  protected FlutterSettingsStepFixture(@NotNull Robot robot, @NotNull JRootPane target) {
    super(FlutterSettingsStepFixture.class, robot, target);
  }

  @NotNull
  public FlutterSettingsStepFixture enterCompanyDomain(@NotNull String text) {
    JTextComponent textField = findTextFieldWithLabel("Company domain");
    replaceText(textField, text);
    return this;
  }

  @SuppressWarnings("Duplicates")
  @NotNull
  public FlutterSettingsStepFixture enterPackageName(@NotNull String text) {
    LabelWithEditButton editLabel = robot().finder().findByType(target(), LabelWithEditButton.class);

    JButton editButton = robot().finder().findByType(editLabel, JButton.class);
    robot().click(editButton);

    JTextComponent textField = findTextFieldWithLabel("Package name");
    replaceText(textField, text);

    // click "Done"
    robot().click(editButton);
    return this;
  }

  public String getCompanyDomain() {
    return findTextFieldWithLabel("Company domain").getText();
  }

  public String getPackageName() {
    final LabelWithEditButton locationField = robot().finder().findByType(target(), LabelWithEditButton.class);
    return execute(new GuiQuery<String>() {
      @Override
      protected String executeInEDT() throws Throwable {
        String location = locationField.getText();
        assertThat(location).isNotEmpty();
        return location;
      }
    });
  }

  public JCheckBoxFixture getKotlinFixture() {
    return findCheckBoxWithText(FlutterBundle.message("module.wizard.language.name_kotlin"));
  }

  public JCheckBoxFixture getSwiftFixture() {
    return findCheckBoxWithText(FlutterBundle.message("module.wizard.language.name_swift"));
  }

  @NotNull
  public FlutterSettingsStepFixture setKotlinSupport(boolean select) {
    selectCheckBoxWithText(FlutterBundle.message("module.wizard.language.name_kotlin"), select);
    return this;
  }

  @NotNull
  public FlutterSettingsStepFixture setSwiftSupport(boolean select) {
    selectCheckBoxWithText(FlutterBundle.message("module.wizard.language.name_swift"), select);
    return this;
  }

  protected JCheckBoxFixture findCheckBoxWithText(@NotNull String text) {
    JCheckBox cb =
      (JCheckBox)robot().finder().find((c) -> c instanceof JCheckBox && c.isShowing() && ((JCheckBox)c).getText().equals(text));
    return new JCheckBoxFixture(robot(), cb);
  }

  @Override
  @NotNull
  protected JCheckBoxFixture selectCheckBoxWithText(@NotNull String text, boolean select) {
    JCheckBoxFixture checkBox = findCheckBoxWithText(text);
    if (select) {
      checkBox.select();
    }
    else {
      checkBox.deselect();
    }
    return checkBox;
  }
}
