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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

public class FlutterFrameFixture extends IdeFrameFixture {
  private FlutterFrameFixture(@NotNull Robot robot, @NotNull IdeFrameImpl target) {
    super(robot, target);
  }

  @NotNull
  public static FlutterFrameFixture find(@NotNull final Robot robot) {
    return new FlutterFrameFixture(robot, GuiTests.waitUntilShowing(robot, Matchers.byType(IdeFrameImpl.class)));
  }
}
