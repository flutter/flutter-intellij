/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.android.tools.idea.tests.gui.framework.fixture;

import javax.swing.JMenuItem;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JMenuItemFixture;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("UnusedReturnValue")
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
