/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.tests.gui.fixtures

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.fixtures.JBCheckBoxFixture
import com.intellij.testGuiFramework.fixtures.JComponentFixture
import com.intellij.testGuiFramework.fixtures.ToolWindowFixture
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.testGuiFramework.matcher.ClassNameMatcher
import com.intellij.testGuiFramework.util.step
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import io.flutter.inspector.HeapDisplay
import io.flutter.view.PerfFPSTab
import io.flutter.view.PerfMemoryTab
import org.fest.swing.core.ComponentFinder
import org.fest.swing.core.Robot
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause.pause
import java.awt.Component
import javax.swing.JPanel

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
      pause(object : Condition("Initialize perf") {
        override fun test(): Boolean {
          return contents[0].displayName != null
        }
      }, Timeouts.seconds30)
      GuiTestUtilKt.waitForBackgroundTasks(myRobot)
    }
  }

  fun memoryTabFixture(): MemoryTabFixture {
    showTab(1, contents)
    val base = perfPanel(classMatcher(PerfMemoryTab::class.java))
    return MemoryTabFixture(base)
  }

  fun perfTabFixture(): PerfTabFixture {
    showTab(0, contents)
    val base = perfPanel(classMatcher(PerfFPSTab::class.java))
    return PerfTabFixture(base)
  }

  private fun <T : Component> perfPanel(matcher: ClassNameMatcher<T>): T {
    val panelRef = Ref<T>()

    pause(object : Condition("Perf panel shows up") {
      override fun test(): Boolean {
        val panels = finder().findAll(contents[0].component, matcher)
        if (panels.isEmpty()) {
          return false
        }
        else {
          panelRef.set(panels.first())
          return true
        }
      }
    }, Timeouts.seconds10)

    return panelRef.get()!!
  }

  private fun finder(): ComponentFinder {
    return ideFrame.robot().finder()
  }

  private fun <T : Component> classMatcher(base: Class<T>): ClassNameMatcher<T> {
    return ClassNameMatcher.forClass(base.name, base)
  }

  inner class MemoryTabFixture(val memoryTab: PerfMemoryTab) {

    fun heapDisplayLabels(): Collection<JBLabel> {
      return finder().findAll(memoryTab, classMatcher(JBLabel::class.java))
    }

    fun heapDisplay(): HeapDisplayFixture {
      return HeapDisplayFixture(myRobot,
          finder().find(memoryTab, classMatcher(HeapDisplay::class.java)))
    }
  }

  inner class PerfTabFixture(val fpsTab: PerfFPSTab) {

    fun controlCheckboxes(): Collection<JBCheckBox> {
      return finder().findAll(fpsTab, classMatcher(JBCheckBox::class.java))
    }

    fun performanceCheckbox(): JBCheckBoxFixture {
      return JBCheckBoxFixture.findByText("Show Performance Overlay", fpsTab, myRobot, true)
    }

    fun repaintCheckbox(): JBCheckBoxFixture {
      return JBCheckBoxFixture.findByText("Show Repaint Rainbow", fpsTab, myRobot, true)
    }

    fun frameRenderingPanel(): FrameRenderingPanelFixture {
      // FrameRenderingPanel is not public, but we only need JPanel protocol, like componentCount.
      return FrameRenderingPanelFixture(myRobot,
          finder().find(fpsTab, ClassNameMatcher.forClass("io.flutter.inspector.FrameRenderingPanel", JPanel::class.java)))
    }
  }

  inner class FrameRenderingPanelFixture(robot: Robot, target: JPanel)
    : JComponentFixture<FrameRenderingPanelFixture, JPanel>(FrameRenderingPanelFixture::class.java, robot, target) {

    fun componentCount(): Int {
      return GuiTestUtilKt.computeOnEdt { target().componentCount }!!
    }
  }

  inner class HeapDisplayFixture(robot: Robot, target: JPanel)
    : JComponentFixture<HeapDisplayFixture, JPanel>(HeapDisplayFixture::class.java, robot, target)
}