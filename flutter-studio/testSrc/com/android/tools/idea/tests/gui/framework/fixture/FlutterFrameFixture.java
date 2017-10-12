/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

public class FlutterFrameFixture extends IdeaFrameFixture {
  private FlutterFrameFixture(@NotNull Robot robot, @NotNull IdeFrameImpl target) {
    super(robot, target);
  }

  @NotNull
  public static FlutterFrameFixture find(@NotNull final Robot robot) {
    return new FlutterFrameFixture(robot, GuiTests.waitUntilShowing(robot, Matchers.byType(IdeFrameImpl.class)));
  }

  public void waitForProjectSyncToFinish() {
    GuiTests.waitForBackgroundTasks(robot());
  }
}
