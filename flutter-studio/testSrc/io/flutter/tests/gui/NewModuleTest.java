/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.tests.gui;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.FlutterGuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.FlutterFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.FlutterProjectStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.FlutterSettingsStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewFlutterModuleWizardFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import com.intellij.testGuiFramework.launcher.GuiTestOptions;
import io.flutter.module.FlutterProjectType;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(NewModuleTest.GuiTestRemoteRunner.class)
public class NewModuleTest {

  @Rule public final FlutterGuiTestRule myGuiTest = new FlutterGuiTestRule();

  @Test
  public void createNewAppModule() throws IOException {
    System.out.println("TEST_DIR="+System.getProperty("TEST_DIR"));
    FlutterFrameFixture ideFrame = myGuiTest.importSimpleApplication();
    EditorFixture editor = ideFrame.getEditor();
    editor.waitUntilErrorAnalysisFinishes();

    NewFlutterModuleWizardFixture wizardFixture =
      ideFrame.openFromMenu(NewFlutterModuleWizardFixture::find, "File", "New", "New Module...");
    wizardFixture.chooseModuleType("Flutter Package").clickNext();
    NewFlutterModuleWizardFixture wizard = ideFrame.findNewModuleWizard();

    FlutterProjectStepFixture projectStep = wizard.getFlutterProjectStep(FlutterProjectType.PACKAGE);
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
    // TODO(messick) Fix SDK path tests
    //String path = projectStep.getSdkPath();
    //projectStep.enterSdkPath("");
    //// This does not work. The message comes back as " ". It does work in manual testing.
    ////assertThat(projectStep.getErrorMessage()).endsWith(("not given."));
    //projectStep.enterSdkPath("x");
    //assertThat(projectStep.getErrorMessage()).endsWith(("not exist."));
    //projectStep.enterSdkPath("/tmp");
    //assertThat(projectStep.getErrorMessage()).endsWith(("location."));
    //projectStep.enterSdkPath(path);

    wizard.clickFinish();
    myGuiTest.waitForBackgroundTasks();
    myGuiTest.ideFrame().waitForProjectSyncToFinish();
  }

  /**
   * This custom runner sets a custom path to the GUI tests.
   * This needs to be done by the test runner because the test framework
   * initializes the path before the test class is loaded.
   */
  public static class GuiTestRemoteRunner extends com.intellij.testGuiFramework.framework.GuiTestRemoteRunner {

    public GuiTestRemoteRunner(Class<?> suiteClass) {
      super(suiteClass);
      System.setProperty("gui.tests.root.dir.path", new java.io.File("testSrc").getAbsolutePath());
      //System.setProperty(GuiTestOptions.IS_RUNNING_ON_RELEASE, "true");
      //System.setProperty(GuiTestOptions.REMOTE_IDE_PATH_KEY, "/Applications/Android Studio.app/Contents/MacOS/studio");
      //System.setProperty(GuiTestOptions.REMOTE_IDE_VM_OPTIONS_PATH_KEY, "/Applications/Android Studio.app/Contents/bin/studio.vmoptions");
    }
  }
}
