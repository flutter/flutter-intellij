/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.tests.gui.fixtures

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.fixtures.ToolWindowFixture
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.matcher.ClassNameMatcher
import io.flutter.inspector.InspectorService
import io.flutter.view.InspectorPanel
import junit.framework.Assert.assertNotNull
import org.fest.swing.core.Robot
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause
import org.fest.swing.timing.Pause.pause
import javax.swing.JPanel

class FlutterInspectorFixture(project: Project, robot: Robot, private val ideFrame: IdeFrameFixture) : ToolWindowFixture(
    "Flutter Inspector", project, robot) {

  fun populate() {
    activate()
    Pause.pause(object : Condition("Initialize inspector") {
      override fun test(): Boolean {
        return contents[0].displayName != null
      }
    }, Timeouts.seconds30)
  }

  fun widgetTreeFixture(): InspectorPanelFixture {
    return inspectorPanel(InspectorService.FlutterTreeType.widget)
  }

  fun renderTreeFixture(): InspectorPanelFixture {
    return inspectorPanel(InspectorService.FlutterTreeType.renderObject)
  }

  private fun inspectorPanel(type: InspectorService.FlutterTreeType): InspectorPanelFixture {
    val inspectorPanelRef = Ref<InspectorPanel>()

    pause(object : Condition("Inspector with type '$type' shows up") {
      override fun test(): Boolean {
        val inspectorPanel = findInspectorPanel(type)
        inspectorPanelRef.set(inspectorPanel)
        return inspectorPanel != null
      }
    }, Timeouts.seconds10)

    val notificationPanel = inspectorPanelRef.get()
    assertNotNull(notificationPanel)
    return InspectorPanelFixture(notificationPanel)
  }

  private fun findInspectorPanel(type: InspectorService.FlutterTreeType): InspectorPanel? {
    val panels = ideFrame.robot().finder().findAll(contents[0].component, ClassNameMatcher.forClass("io.flutter.view.InspectorPanel", JPanel::class.java))
    return panels.firstOrNull { (it as InspectorPanel).treeType == type } as InspectorPanel
  }

  inner class InspectorPanelFixture(val inspectorPanel: InspectorPanel) {

  }
}
