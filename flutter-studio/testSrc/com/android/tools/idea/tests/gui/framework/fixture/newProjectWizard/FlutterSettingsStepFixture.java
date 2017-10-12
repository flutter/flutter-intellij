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
import io.flutter.FlutterBundle;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;

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
}
