/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard;

import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardStepFixture;
import io.flutter.FlutterBundle;
import javax.swing.JCheckBox;
import javax.swing.JRootPane;
import javax.swing.text.JTextComponent;
import org.fest.swing.fixture.JCheckBoxFixture;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"UnusedReturnValue", "unused"})
public class FlutterSettingsStepFixture<W extends AbstractWizardFixture>
  extends AbstractWizardStepFixture<FlutterSettingsStepFixture, W> {
  protected FlutterSettingsStepFixture(@NotNull W wizard, @NotNull JRootPane target) {
    super(FlutterSettingsStepFixture.class, wizard, target);
  }

  @NotNull
  public FlutterSettingsStepFixture enterPackageName(@NotNull String text) {
    JTextComponent textField = findTextFieldWithLabel("Package name");
    replaceText(textField, text);
    return this;
  }

  public String getPackageName() {
    return findTextFieldWithLabel("Package name").getText();
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
