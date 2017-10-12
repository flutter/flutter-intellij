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

import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JMenuItemFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class MenuItemFixture extends JMenuItemFixture {
  public MenuItemFixture(@NotNull Robot robot, @NotNull JMenuItem target) {
    super(robot, target);
  }

  /**
   * This is a replacement for AbstractComponentFixture.click(). The original method does not invoke the click() method of the driver
   * subclass used by JMenuItemFixture. Probably has something to do with type erasure. Ensuring the proper argument type, as is done
   * here, invokes the correct method. Et viola! Mac menus work.
   */
  @NotNull
  public JMenuItemFixture clickit() {
    JMenuItem target = target();
    driver().click(target);
    return myself();
  }
}
