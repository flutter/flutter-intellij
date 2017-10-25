/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.tests.gui;

import com.android.tools.idea.tests.gui.framework.FlutterGuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.FlutterFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.*;
import io.flutter.module.FlutterProjectType;
import io.flutter.project.FlutterSettingsStep;
import io.flutter.tests.util.WizardUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class NewModuleTest {
  @Rule public final FlutterGuiTestRule myGuiTest = new FlutterGuiTestRule();

  @Test
  public void createNewAppModule() {
    WizardUtils.createNewApplication(myGuiTest);
    FlutterFrameFixture ideFrame = myGuiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor();
    editor.waitUntilErrorAnalysisFinishes();

    NewFlutterModuleWizardFixture wizardFixture = ideFrame.openFromMenu(NewFlutterModuleWizardFixture::find, "File", "New", "New Module...");
    wizardFixture.chooseModuleType("Flutter Application").clickNext();
    NewFlutterModuleWizardFixture wizard = ideFrame.findNewModuleWizard();

    FlutterProjectStepFixture projectStep = wizard.getFlutterProjectStep(FlutterProjectType.APP);
    assertThat(projectStep.isConfiguredForModules()).isTrue();

    // Check error messages.
    assertThat(projectStep.getErrorMessage()).isNotNull();
    projectStep.enterProjectName("");
    assertThat(projectStep.getErrorMessage()).startsWith("Please enter");
    projectStep.enterProjectName("A");
    assertThat(projectStep.getErrorMessage()).contains("valid Dart package");
    projectStep.enterProjectName("class");
    assertThat(projectStep.getErrorMessage()).contains("Dart keyword");
    projectStep.enterProjectName("utf");
    assertThat(projectStep.getErrorMessage()).contains("Flutter package");
    projectStep.enterProjectName("a_long_module_name_is_not_allowed");
    assertThat(projectStep.getErrorMessage()).contains("less than");

    projectStep.enterProjectName("module");
    String path = projectStep.getSdkPath();
    projectStep.enterSdkPath("");
    // This does not work. The message comes back as " ". It does work in manual testing.
    //assertThat(projectStep.getErrorMessage()).endsWith(("not given."));
    projectStep.enterSdkPath("x");
    assertThat(projectStep.getErrorMessage()).endsWith(("not exist."));
    projectStep.enterSdkPath("/tmp");
    assertThat(projectStep.getErrorMessage()).endsWith(("location."));
    projectStep.enterSdkPath(path);
    wizard.clickNext();

    FlutterSettingsStepFixture settingsStep = wizard.getFlutterSettingsStep();
    settingsStep.enterCompanyDomain("flutter.io");

    wizard.clickFinish();
    myGuiTest.waitForBackgroundTasks();
    myGuiTest.ideFrame().waitForProjectSyncToFinish();
  }
}
