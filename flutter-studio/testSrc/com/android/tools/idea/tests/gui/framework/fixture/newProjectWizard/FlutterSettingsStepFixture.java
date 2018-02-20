/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard;

import com.android.tools.adtui.LabelWithEditButton;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardStepFixture;
import io.flutter.FlutterBundle;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.JCheckBoxFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;

import static com.google.common.truth.Truth.assertThat;
import static org.fest.swing.edt.GuiActionRunner.execute;

public class FlutterSettingsStepFixture<W extends AbstractWizardFixture>
  extends AbstractWizardStepFixture<FlutterSettingsStepFixture, W> {
  protected FlutterSettingsStepFixture(@NotNull W wizard, @NotNull JRootPane target) {
    super(FlutterSettingsStepFixture.class, wizard, target);
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
