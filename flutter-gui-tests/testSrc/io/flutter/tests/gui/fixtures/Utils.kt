/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.tests.gui.fixtures

import com.intellij.execution.ui.layout.impl.JBRunnerTabs
import com.intellij.testGuiFramework.fixtures.JComponentFixture
import com.intellij.testGuiFramework.impl.GuiRobotHolder
import com.intellij.ui.content.Content
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.newImpl.TabLabel
import org.fest.swing.core.Robot

fun showTab(index: Int, contents: Array<Content>) {
  val tabs: JBRunnerTabs = contents[0].component.components[0] as JBRunnerTabs
  val info: TabInfo = tabs.getTabAt(index)
  val label = tabs.getTabLabel(info)
  TabLabelFixture(GuiRobotHolder.robot, label).click()
}

// A clickable fixture for the three tabs in the inspector: Widgets, Render Tree, and Performance.
class TabLabelFixture(robot: Robot, target: TabLabel)
  : JComponentFixture<TabLabelFixture, TabLabel>(TabLabelFixture::class.java, robot, target)
