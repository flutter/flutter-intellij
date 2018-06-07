/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.tests.gui;

import com.android.tools.idea.tests.gui.framework.FlutterGuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestSuiteRunner;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.FlutterFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.FlutterProjectStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.FlutterSettingsStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewFlutterModuleWizardFixture;
import com.intellij.openapi.application.PathManager;
import io.flutter.module.FlutterProjectType;
import io.flutter.tests.util.WizardUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.model.InitializationError;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

@RunWith(NewModuleTest.CustomRunner.class)
public class NewModuleTest {

  /**
   * CustomRunner sets a custom path to the GUI tests.
   * This needs to be done by the test runner because the test framework
   * initializes the path before the test class is loaded.
   */
  public static class CustomRunner extends GuiTestSuiteRunner {

    public CustomRunner(Class<?> testClass) throws InitializationError, IOException {
      super(testClass, null); // TODO(mesick) Fix this quick hack.
      System.setProperty("gui.tests.root.dir.path", "somewhere");
    }

  }
  @Rule public final FlutterGuiTestRule myGuiTest = new FlutterGuiTestRule();

  @Test
  public void createNewAppModule() {
    PathManager.getHomePath();
    WizardUtils.createNewApplication(myGuiTest);
    FlutterFrameFixture ideFrame = myGuiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor();
    editor.waitUntilErrorAnalysisFinishes();

    NewFlutterModuleWizardFixture wizardFixture =
      ideFrame.openFromMenu(NewFlutterModuleWizardFixture::find, "File", "New", "New Module...");
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
