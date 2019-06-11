/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.tests.gui

import com.intellij.testGuiFramework.fixtures.ExecutionToolWindowFixture
import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.framework.RunWithIde
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.launcher.ide.CommunityIde
import com.intellij.testGuiFramework.util.step
import io.flutter.tests.gui.fixtures.flutterPerfFixture
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause
import org.junit.Test

@RunWithIde(CommunityIde::class)
class PerfTest : GuiTestCase() {

  @Test
  fun widgetTree() {
    ProjectCreator.importProject()
    ideFrame {
      launchFlutterApp()
      val inspector = flutterPerfFixture(this)
      inspector.populate()
    }
  }

  // TODO Share with InspectorTest
  fun IdeFrameFixture.launchFlutterApp() {
    step("Launch Flutter app") {
      findRunApplicationButton().click()
      val runner = runner()
      Pause.pause(object : Condition("Start app") {
        override fun test(): Boolean {
          return runner.isExecutionInProgress
        }
      }, Timeouts.seconds30)
    }
  }

  // TODO Share with InspectorTest
  private fun IdeFrameFixture.runner(): ExecutionToolWindowFixture.ContentFixture {
    return runToolWindow.findContent("main.dart")
  }
}