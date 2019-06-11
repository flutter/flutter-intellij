/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.tests.gui.fixtures

import com.intellij.openapi.project.Project
import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.fixtures.ToolWindowFixture
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.util.step
import org.fest.swing.core.Robot
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause

fun IdeFrameFixture.flutterPerfFixture(ideFrame: IdeFrameFixture): FlutterPerfFixture {
  return FlutterPerfFixture(project, robot(), ideFrame)
}

// A fixture for the Performance top-level view.
class FlutterPerfFixture(project: Project, robot: Robot, private val ideFrame: IdeFrameFixture)
  : ToolWindowFixture("Flutter Performance", project, robot) {

  fun populate() {
    step("Populate perf view") {
      activate()
      selectedContent
      Pause.pause(object : Condition("Initialize perf") {
        override fun test(): Boolean {
          return contents[0].displayName != null
        }
      }, Timeouts.seconds30)
    }
  }

  fun memoryTabFixture(): MemoryTabFixture {
    return MemoryTabFixture();
  }

  fun perfTabFixture(): PerfTabFixture {
    return PerfTabFixture();
  }

  inner class MemoryTabFixture {

  }

  inner class PerfTabFixture {

  }
}