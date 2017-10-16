/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard;

import com.android.tools.adtui.ASGallery;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.progress.ProgressManager;
import io.flutter.module.FlutterProjectType;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

// Adapted from com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture
public class NewFlutterProjectWizardFixture extends AbstractWizardFixture<NewFlutterProjectWizardFixture> {

  @NotNull
  public static NewFlutterProjectWizardFixture find(@NotNull Robot robot) {
    JDialog dialog = GuiTests.waitUntilShowing(robot, Matchers.byTitle(JDialog.class, "Create New Flutter Project"));
    return new NewFlutterProjectWizardFixture(robot, dialog);
  }

  private NewFlutterProjectWizardFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(NewFlutterProjectWizardFixture.class, robot, target);
  }

  public NewFlutterProjectWizardFixture chooseProjectType(@NotNull String activity) {
    JListFixture listFixture = new JListFixture(robot(), robot().finder().findByType(target(), ASGallery.class));
    listFixture.replaceCellReader((jList, index) -> String.valueOf(jList.getModel().getElementAt(index)));
    listFixture.clickItem(activity);
    return this;
  }

  @NotNull
  public FlutterProjectStepFixture getFlutterProjectStep(@NotNull FlutterProjectType type) {
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
    return new FlutterProjectStepFixture(robot(), rootPane);
  }

  @NotNull
  public FlutterSettingsStepFixture getFlutterSettingsStep() {
    JRootPane rootPane = findStepWithTitle("Set the package name");
    return new FlutterSettingsStepFixture(robot(), rootPane);
  }

  @NotNull
  @Override
  public NewFlutterProjectWizardFixture clickFinish() {
    super.clickFinish();

    // Wait for 'flutter create' to finish
    Wait.seconds(30).expecting("Modal Progress Indicator to finish")
      .until(() -> {
        robot().waitForIdle();
        return !ProgressManager.getInstance().hasModalProgressIndicator();
      });
    return myself();
  }
}
