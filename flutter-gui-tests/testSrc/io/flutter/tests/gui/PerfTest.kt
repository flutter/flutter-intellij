/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.tests.gui

import com.intellij.testGuiFramework.framework.RunWithIde
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.launcher.ide.CommunityIde
import com.intellij.testGuiFramework.util.step
import io.flutter.tests.gui.fixtures.flutterPerfFixture
import org.fest.swing.timing.Pause.pause
import org.junit.Test
import kotlin.test.expect

@RunWithIde(CommunityIde::class)
class PerfTest : GuiTestCase() {

  @Test
  fun test() {
    ProjectCreator.importProject()
    ideFrame {
      launchFlutterApp()
      val monitor = flutterPerfFixture(this)
      monitor.populate()
      pause()

      step("Exercise FPS tab") {
        val perf = monitor.perfTabFixture()
        expect(false, perf.controlCheckboxes()::isEmpty)
        val performanceCheckbox = perf.performanceCheckbox()
        expect(true, performanceCheckbox::isEnabled)
        val repaintCheckbox = perf.repaintCheckbox()
        expect(true, repaintCheckbox::isEnabled)
        val frames = perf.frameRenderingPanel()
        var currCount = frames.componentCount()
        var prevCount = currCount
        expect(true) { currCount >= 0 }

        step("Drive UI and cause app to refresh") {
          performanceCheckbox.click()
          performanceCheckbox.click()
          currCount = frames.componentCount()
          expect(true) { currCount > prevCount }
          prevCount = currCount
          repaintCheckbox.click()
          repaintCheckbox.click()
          currCount = frames.componentCount()
          expect(true) { currCount > prevCount }
          prevCount = currCount
        }
      }

      step("Check memory tab") {
        val mem = monitor.memoryTabFixture()
        expect(false, mem.heapDisplayLabels()::isEmpty)
        expect(true, mem.heapDisplay()::isEnabled)
      }
      runner().stop()
    }
  }
}