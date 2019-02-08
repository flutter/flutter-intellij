/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard;

import static com.google.common.collect.Lists.newArrayList;

import com.android.tools.adtui.ASGallery;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import io.flutter.module.FlutterProjectType;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JRootPane;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

// Adapted from com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture
@SuppressWarnings("UnusedReturnValue")
public class NewFlutterProjectWizardFixture extends AbstractWizardFixture<NewFlutterProjectWizardFixture> {

  private NewFlutterProjectWizardFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(NewFlutterProjectWizardFixture.class, robot, target);
  }

  @NotNull
  public static NewFlutterProjectWizardFixture find(@NotNull Robot robot) {
    JDialog dialog = GuiTests.waitUntilShowing(robot, Matchers.byTitle(JDialog.class, "Create New Flutter Project"));
    return new NewFlutterProjectWizardFixture(robot, dialog);
  }

  public NewFlutterProjectWizardFixture chooseProjectType(@NotNull String activity) {
    JListFixture listFixture = new JListFixture(robot(), robot().finder().findByType(target(), ASGallery.class));
    listFixture.replaceCellReader((jList, index) -> String.valueOf(jList.getModel().getElementAt(index)));
    listFixture.clickItem(activity);
    return this;
  }

  @NotNull
  public FlutterProjectStepFixture<NewFlutterProjectWizardFixture> getFlutterProjectStep(@NotNull FlutterProjectType type) {
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
  public NewFlutterProjectWizardFixture clickFinish() {
    List<Project> previouslyOpenProjects = newArrayList(ProjectManager.getInstance().getOpenProjects());
    super.clickFinish(Wait.seconds(10));

    List<Project> newOpenProjects = newArrayList();
    Wait.seconds(5).expecting("Project to be created")
      .until(() -> {
        newOpenProjects.addAll(newArrayList(ProjectManager.getInstance().getOpenProjects()));
        newOpenProjects.removeAll(previouslyOpenProjects);
        return !newOpenProjects.isEmpty();
      });

    // Wait for 'flutter create' to finish
    Wait.seconds(30).expecting("Modal Progress Indicator to finish")
      .until(() -> {
        robot().waitForIdle();
        return !ProgressManager.getInstance().hasModalProgressIndicator();
      });
    return myself();
  }
}
