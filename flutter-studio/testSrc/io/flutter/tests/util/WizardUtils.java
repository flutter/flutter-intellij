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
package io.flutter.tests.util;

import com.android.tools.idea.tests.gui.framework.FlutterGuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewFlutterProjectWizardFixture;
import org.jetbrains.annotations.NotNull;

public class WizardUtils {
  private WizardUtils(){}

  public static void createNewApplication(@NotNull FlutterGuiTestRule guiTest) {
    createNewProject(guiTest, "Flutter Application");
  }

  public static void createNewProject(@NotNull FlutterGuiTestRule guiTest, @NotNull String projectType) {
    NewFlutterProjectWizardFixture wizard = guiTest.welcomeFrame().createNewProject();

    wizard.chooseProjectType(projectType);
    wizard.clickNext();

    wizard.getFlutterProjectStep().enterSdkPath("/Users/messick/src/flutter/flutter"); // TODO(messick): Parameterize SDK.
    wizard.clickNext();

    wizard.getFlutterSettingsStep().enterCompanyDomain("flutter.io");
    wizard.clickFinish();

    guiTest.ideFrame().waitForProjectSyncToFinish();
  }
}
