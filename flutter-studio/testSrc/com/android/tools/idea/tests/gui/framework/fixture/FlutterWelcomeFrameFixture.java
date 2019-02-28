/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewFlutterProjectWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.sdk.SdkProblemDialogFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

// Adapted from com.android.tools.idea.tests.gui.framework.fixture.WelcomeFrameFixture
@SuppressWarnings("SameParameterValue")
public class FlutterWelcomeFrameFixture extends ComponentFixture<FlutterWelcomeFrameFixture, FlatWelcomeFrame> {
  private static final String NEW_PROJECT_WELCOME_ID = "flutter.NewProject.welcome"; // See META-INF/studio-contribs.xml

  private FlutterWelcomeFrameFixture(@NotNull Robot robot, @NotNull FlatWelcomeFrame target) {
    super(FlutterWelcomeFrameFixture.class, robot, target);
  }

  @SuppressWarnings("WeakerAccess")
  @NotNull
  public static FlutterWelcomeFrameFixture find(@NotNull Robot robot) {
    return new FlutterWelcomeFrameFixture(robot, GuiTests.waitUntilShowing(robot, Matchers.byType(FlatWelcomeFrame.class)));
  }

  @NotNull
  public static FlutterWelcomeFrameFixture find(@NotNull IdeaFrameFixture ideFrameFixture) {
    return find(ideFrameFixture.robot());
  }

  public SdkProblemDialogFixture createNewProjectWhenSdkIsInvalid() {
    findActionLinkByActionId(NEW_PROJECT_WELCOME_ID).click();
    return SdkProblemDialogFixture.find(this);
  }

  @NotNull
  public NewFlutterProjectWizardFixture createNewProject() {
    findActionLinkByActionId(NEW_PROJECT_WELCOME_ID).click();
    return NewFlutterProjectWizardFixture.find(robot());
  }

  @NotNull
  private ActionLinkFixture findActionLinkByActionId(String actionId) {
    return ActionLinkFixture.findByActionId(actionId, robot(), target());
  }
}
