/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package com.android.tools.idea.tests.gui.framework.fixture;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import java.awt.Container;
import java.util.Arrays;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.fest.swing.core.Robot;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

// Use the MenuItemFixture from fest to control Mac menus.
// This is currently unused. If menus work properly now this class should be deleted.
public class MacMenuFixture extends MenuFixture {

  @NotNull private final Robot myRobot;
  @NotNull private final IdeFrameImpl myContainer;

  MacMenuFixture(@NotNull Robot robot, @NotNull IdeFrameImpl container) {
    super(robot, container);
    myRobot = robot;
    myContainer = container;
  }

  /**
   * Invokes an action by menu path
   *
   * @param path the series of menu names, e.g. {@link invokeActionByMenuPath("Build", "Make Project ")}
   */
  void invokeMenuPath(@NotNull String... path) {
    JMenuItem menuItem = findActionMenuItem(path);
    assertWithMessage("Menu path \"" + Joiner.on(" -> ").join(path) + "\" is not enabled").that(menuItem.isEnabled()).isTrue();
    new MenuItemFixture(myRobot, menuItem).clickit();
  }

  @NotNull
  private JMenuItem findActionMenuItem(@NotNull String... path) {
    myRobot.waitForIdle(); // UI events can trigger modifications of the menu contents
    assertThat(path).isNotEmpty();
    int segmentCount = path.length;

    // We keep the list of previously found pop-up menus, so we don't look for menu items in the same pop-up more than once.
    List<JPopupMenu> previouslyFoundPopups = Lists.newArrayList();

    Container root = myContainer;
    for (int i = 0; i < segmentCount; i++) {
      final String segment = path[i];
      JMenuItem found = GuiTests.waitUntilShowingAndEnabled(myRobot, root, Matchers.byText(JMenuItem.class, segment));
      if (root instanceof JPopupMenu) {
        previouslyFoundPopups.add((JPopupMenu)root);
      }
      if (i < segmentCount - 1) {
        new MenuItemFixture(myRobot, found).clickit();
        List<JPopupMenu> showingPopupMenus = findShowingPopupMenus(i + 1);
        showingPopupMenus.removeAll(previouslyFoundPopups);
        assertThat(showingPopupMenus).hasSize(1);
        root = showingPopupMenus.get(0);
        continue;
      }
      return found;
    }
    throw new AssertionError("Menu item with path " + Arrays.toString(path) + " should have been found already");
  }

  @SuppressWarnings("Duplicates")
  @NotNull
  private List<JPopupMenu> findShowingPopupMenus(final int expectedCount) {
    final Ref<List<JPopupMenu>> ref = new Ref<>();
    Wait.seconds(5).expecting(expectedCount + " JPopupMenus to show up")
      .until(() -> {
        List<JPopupMenu> popupMenus = Lists.newArrayList(myRobot.finder().findAll(Matchers.byType(JPopupMenu.class).andIsShowing()));
        boolean allFound = popupMenus.size() == expectedCount;
        if (allFound) {
          ref.set(popupMenus);
        }
        return allFound;
      });
    List<JPopupMenu> popupMenus = ref.get();
    assertThat(popupMenus).hasSize(expectedCount);
    return popupMenus;
  }
}
