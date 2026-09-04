/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.tests.gui;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.idea.tests.gui.framework.FlutterGuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.FlutterProjectStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.FlutterSettingsStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewFlutterProjectWizardFixture;
import io.flutter.module.FlutterProjectType;
import io.flutter.tests.util.WizardUtils;
import org.fest.swing.exception.WaitTimedOutError;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * As long as the wizard is working properly the error checks
 * in FlutterProjectCreator will never be triggered. That leaves
 * quite a few lines untested. It currently has 79% coverage.
 * <p>
 * The "Install SDK" button of FlutterProjectStep is not tested.
 * It has 86% coverage currently, and most of the untested code
 * is part of the installer implementation.
 * <p>
 * If flakey tests are found try adjusting these settings:
 * Settings festSettings = myGuiTest.robot().settings();
 * festSettings.delayBetweenEvents(50); // 30
 * festSettings.eventPostingDelay(150); // 100
 */
@RunWith(NewModuleTest.GuiTestRemoteRunner.class)//@RunWith(GuiTestSuiteRunner.class)
public class NewProjectTest {
  @Rule public final FlutterGuiTestRule myGuiTest = new FlutterGuiTestRule();

  @Test
  public void createNewProjectWithDefaults() {
    NewFlutterProjectWizardFixture wizard = myGuiTest.welcomeFrame().createNewProject();
    try {
      wizard.clickNext().clickNext().clickFinish();
      myGuiTest.waitForBackgroundTasks();
      myGuiTest.ideFrame().waitForProjectSyncToFinish();
    }
    catch (Exception ex) {
      // If this happens to be the first test run in a suite then there will be no SDK and it times out.
      assertThat(ex.getClass()).isAssignableTo(WaitTimedOutError.class);
      assertThat(ex.getMessage()).isEqualTo("Timed out waiting for matching JButton");
      wizard.clickCancel();
    }
  }

  @Test
  public void createNewApplicationWithDefaults() {
    WizardUtils.createNewApplication(myGuiTest);
    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    editor.waitUntilErrorAnalysisFinishes();
    String expected =
      "import 'package:flutter/material.dart';\n" +
      "\n" +
      "void main() => runApp(MyApp());\n";

    assertEquals(expected, editor.getCurrentFileContents().substring(0, expected.length()));
  }

  @Test
  public void createNewModuleWithDefaults() {
    WizardUtils.createNewModule(myGuiTest);
    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    editor.waitUntilErrorAnalysisFinishes();
    String expected =
      "import 'package:flutter/material.dart';\n" +
      "\n" +
      "void main() => runApp(MyApp());\n";

    assertEquals(expected, editor.getCurrentFileContents().substring(0, expected.length()));
  }

  @Test
  public void createNewPackageWithDefaults() {
    WizardUtils.createNewPackage(myGuiTest);
    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    editor.waitUntilErrorAnalysisFinishes();
    String expected =
      "# flutterpackage\n" +
      "\n" +
      "A new Flutter package.\n" +
      "\n" +
      "## Getting Started\n" +
      "\n" +
      "This project is a starting point for a Dart\n" +
      "[package]";

    assertEquals(expected, editor.getCurrentFileContents().substring(0, expected.length()));
  }

  @Test
  public void createNewPluginWithDefaults() {
    WizardUtils.createNewPlugin(myGuiTest);
    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    editor.waitUntilErrorAnalysisFinishes();
    String expected =
      "import 'dart:async';\n" +
      "\n" +
      "import 'package:flutter/services.dart';\n" +
      "\n" +
      "class Flutterplugin";

    assertEquals(expected, editor.getCurrentFileContents().substring(0, expected.length()));
  }

  @Test
  public void checkPersistentState() {
    FlutterProjectType type = FlutterProjectType.APP;
    WizardUtils.createNewProject(myGuiTest, type, "super_tron", "A super fancy tron", "com.google.super_tron", true, true);
    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    editor.waitUntilErrorAnalysisFinishes();

    myGuiTest.ideFrame().invokeMenuPath("File", "New", "New Flutter Project...");
    NewFlutterProjectWizardFixture wizard = myGuiTest.ideFrame().findNewProjectWizard();
    wizard.chooseProjectType("Flutter Application").clickNext();

    FlutterProjectStepFixture projectStep = wizard.getFlutterProjectStep(type);
    assertThat(projectStep.getProjectName()).isEqualTo("flutter_app"); // Not persisting
    assertThat(projectStep.getSdkPath()).isNotEmpty(); // Persisting
    assertThat(projectStep.getProjectLocation()).endsWith("checkPersistentState"); // Not persisting
    assertThat(projectStep.getDescription()).isEqualTo("A new Flutter application."); // Not persisting
    wizard.clickNext();

    FlutterSettingsStepFixture settingsStep = wizard.getFlutterSettingsStep();
    assertThat(settingsStep.getPackageName()).isEqualTo("com.google.flutterapp"); // Partially persisting
    settingsStep.getKotlinFixture().requireSelected(); // Persisting
    settingsStep.getSwiftFixture().requireSelected(); // Persisting
    settingsStep.getKotlinFixture().setSelected(false);
    wizard.clickCancel();

    myGuiTest.ideFrame().invokeMenuPath("File", "New", "New Flutter Project...");
    wizard = myGuiTest.ideFrame().findNewProjectWizard();
    wizard.chooseProjectType("Flutter Application").clickNext();
    wizard.clickNext();

    settingsStep = wizard.getFlutterSettingsStep();
    settingsStep.getKotlinFixture().requireNotSelected(); // Persisting
    settingsStep.getSwiftFixture().requireSelected(); // Independent of Kotlin
    wizard.clickCancel();

    myGuiTest.ideFrame().invokeMenuPath("File", "New", "New Flutter Project...");
    wizard = myGuiTest.ideFrame().findNewProjectWizard();
    wizard.chooseProjectType("Flutter Application").clickNext();
    projectStep = wizard.getFlutterProjectStep(type);
    projectStep.enterProjectLocation("/");
    assertThat(projectStep.getErrorMessage()).contains("location");
    wizard.clickCancel();
  }
}
