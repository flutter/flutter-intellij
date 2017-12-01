/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.tests.util;

import com.android.tools.idea.tests.gui.framework.FlutterGuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewFlutterProjectWizardFixture;
import io.flutter.module.FlutterProjectType;
import org.jetbrains.annotations.NotNull;

public class WizardUtils {
  private WizardUtils() {
  }

  public static void createNewApplication(@NotNull FlutterGuiTestRule guiTest) {
    createNewProject(guiTest, FlutterProjectType.APP, null, null, null, null, null);
  }

  public static void createNewPackage(@NotNull FlutterGuiTestRule guiTest) {
    createNewProject(guiTest, FlutterProjectType.PACKAGE, null, null, null, null, null);
  }

  public static void createNewPlugin(@NotNull FlutterGuiTestRule guiTest) {
    createNewProject(guiTest, FlutterProjectType.PLUGIN, null, null, null, null, null);
  }

  public static void createNewProject(@NotNull FlutterGuiTestRule guiTest, @NotNull FlutterProjectType type,
                                      String name, String description, String domain, Boolean isKotlin, Boolean isSwift) {
    String sdkPath = System.getProperty("flutter.home");
    if (sdkPath == null) {
      // Fail fast if the Flutter SDK is not given.
      System.out.println("Add -Dflutter.home=/path/to/flutter/sdk to the VM options");
      throw new IllegalStateException("flutter.home not set");
    }
    String projectType;
    switch (type) {
      case APP:
        projectType = "Flutter Application";
        break;
      case PACKAGE:
        projectType = "Flutter Package";
        break;
      case PLUGIN:
        projectType = "Flutter Plugin";
        break;
      default:
        throw new IllegalArgumentException();
    }
    NewFlutterProjectWizardFixture wizard = guiTest.welcomeFrame().createNewProject();

    wizard.chooseProjectType(projectType);
    wizard.clickNext();

    if (name != null) {
      wizard.getFlutterProjectStep(type).enterProjectName(name);
    }
    wizard.getFlutterProjectStep(type).enterSdkPath(sdkPath); // TODO(messick): Parameterize SDK.
    if (description != null) {
      wizard.getFlutterProjectStep(type).enterDescription(description);
    }

    if (type == FlutterProjectType.APP || type == FlutterProjectType.PLUGIN) {
      wizard.clickNext();
      if (domain != null) {
        wizard.getFlutterSettingsStep().enterCompanyDomain(domain);
      }
      if (isKotlin != null) {
        wizard.getFlutterSettingsStep().setKotlinSupport(isKotlin);
      }
      if (isSwift != null) {
        wizard.getFlutterSettingsStep().setSwiftSupport(isSwift);
      }
    }
    wizard.clickFinish();

    guiTest.waitForBackgroundTasks();
    guiTest.ideFrame().waitForProjectSyncToFinish();
  }
}
