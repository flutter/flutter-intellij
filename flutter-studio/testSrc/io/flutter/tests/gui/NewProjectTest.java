/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.tests.gui;

import com.android.tools.idea.tests.gui.framework.FlutterGuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * As long as the wizard is working properly the error checks
 * in FlutterProjectCreator will never be triggered. That leaves
 * quite a few lines untested. It currently has 79% coverage.
 *
 * The "Install SDK" button of FlutterProjectStep is not tested.
 * It has 86% coverage currently, and most of the untested code
 * is part of the installer implementation.
 *
 * If flakey tests are found try adjusting these settings:
 * Settings festSettings = myGuiTest.robot().settings();
 * festSettings.delayBetweenEvents(50); // 30
 * festSettings.eventPostingDelay(150); // 100
 */
@RunWith(GuiTestRunner.class)
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
      "void main() => runApp(new MyApp());\n";

    assertEquals(expected, editor.getCurrentFileContents().substring(0, expected.length()));
  }

  @Test
  public void createNewPackageWithDefaults() {
    WizardUtils.createNewPackage(myGuiTest);
    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    editor.waitUntilErrorAnalysisFinishes();
    String expected =
      "library flutter_package;\n" +
      "\n" +
      "/// A Calculator.\n" +
      "class Calculator {\n" +
      "  /// Returns [value] plus 1.\n" +
      "  int addOne(int value) => value + 1;\n" +
      "}\n";

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
      "class FlutterPlugin {\n" +
      "  static const MethodChannel _channel =\n" +
      "      const MethodChannel('flutter_plugin');\n" +
      "\n" +
      "  static Future<String> get platformVersion =>\n" +
      "      _channel.invokeMethod('getPlatformVersion');\n" +
      "}\n";

    assertEquals(expected, editor.getCurrentFileContents().substring(0, expected.length()));
  }

  @Test
  public void checkPersistentState() {
    FlutterProjectType type = FlutterProjectType.APP;
    WizardUtils.createNewProject(myGuiTest, type, "super_tron", "A super fancy tron", "google.com", true, true);
    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    editor.waitUntilErrorAnalysisFinishes();

    myGuiTest.ideFrame().invokeMenuPath("File", "New", "New Flutter Project...");
    NewFlutterProjectWizardFixture wizard = myGuiTest.ideFrame().findNewProjectWizard();
    wizard.chooseProjectType("Flutter Application").clickNext();

    FlutterProjectStepFixture projectStep = wizard.getFlutterProjectStep(type);
    assertThat(projectStep.getProjectName()).isEqualTo("flutter_app"); // Not persisting
    assertThat(projectStep.getSdkPath()).isNotEmpty(); // Persisting
    assertThat(projectStep.getProjectLocation()).endsWith("flutter_app"); // Not persisting
    assertThat(projectStep.getDescription()).isEqualTo("A new Flutter application."); // Not persisting
    wizard.clickNext();

    FlutterSettingsStepFixture settingsStep = wizard.getFlutterSettingsStep();
    assertThat(settingsStep.getCompanyDomain()).isEqualTo("google.com"); // Persisting
    assertThat(settingsStep.getPackageName()).isEqualTo("com.google.flutter_app"); // Partially persisting
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
