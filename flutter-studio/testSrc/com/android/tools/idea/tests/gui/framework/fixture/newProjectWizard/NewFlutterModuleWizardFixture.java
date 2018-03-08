/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard;

import com.android.tools.adtui.ASGallery;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeaFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import io.flutter.module.FlutterProjectType;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickButtonWhenEnabled;

public class NewFlutterModuleWizardFixture extends AbstractWizardFixture<NewFlutterModuleWizardFixture> {

  private NewFlutterModuleWizardFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(NewFlutterModuleWizardFixture.class, robot, target);
  }

  @NotNull
  public static NewFlutterModuleWizardFixture find(@NotNull IdeaFrameFixture fixture) {
    Robot robot = fixture.robot();
    JDialog dialog = GuiTests.waitUntilShowing(robot, Matchers.byTitle(JDialog.class, "Create New Module"));
    return new NewFlutterModuleWizardFixture(robot, dialog);
  }

  public NewFlutterModuleWizardFixture chooseModuleType(@NotNull String activity) {
    JListFixture listFixture = new JListFixture(robot(), robot().finder().findByType(target(), ASGallery.class));
    listFixture.clickItem(activity);
    return this;
  }

  @NotNull
  public FlutterProjectStepFixture<NewFlutterModuleWizardFixture> getFlutterProjectStep(@NotNull FlutterProjectType type) {
    String projectType;
    switch (type) {
      case APP:
        projectType = "application";
        break;
      case PACKAGE:
        projectType = "package";
        break;
      case PLUGIN:
        projectType = "plugin";
        break;
      default:
        throw new IllegalArgumentException();
    }
    JRootPane rootPane = findStepWithTitle("Configure the new Flutter " + projectType);
    return new FlutterProjectStepFixture<>(this, rootPane);
  }

  @NotNull
  public FlutterSettingsStepFixture getFlutterSettingsStep() {
    JRootPane rootPane = findStepWithTitle("Set the package name");
    return new FlutterSettingsStepFixture<>(this, rootPane);
  }

  @NotNull
  public NewFlutterModuleWizardFixture clickFinish() {
    // Do not user superclass method. When the project/module wizard is run from the IDE (not the Welcome screen)
    // the dialog does not disappear within the time allotted by the superclass method.
    findAndClickButtonWhenEnabled(this, "Finish");
    Wait.seconds(30).expecting("dialog to disappear").until(() -> !target().isShowing());
    return myself();
  }
}
