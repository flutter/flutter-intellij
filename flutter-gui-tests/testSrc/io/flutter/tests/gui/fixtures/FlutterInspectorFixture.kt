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
import com.intellij.util.ui.EdtInvocationManager
import io.flutter.inspector.InspectorService
import io.flutter.inspector.InspectorTree
import io.flutter.view.InspectorPanel
import junit.framework.Assert.assertNotNull
import org.fest.swing.core.ComponentFinder
import org.fest.swing.core.Robot
import org.fest.swing.fixture.JTreeFixture
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause
import org.fest.swing.timing.Pause.pause
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.TreePath

class FlutterInspectorFixture(project: Project, robot: Robot, private val ideFrame: IdeFrameFixture)
  : ToolWindowFixture("Flutter Inspector", project, robot) {

  fun populate() {
    activate()
    selectedContent
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

  private fun finder(): ComponentFinder {
    return ideFrame.robot().finder()
  }

  private fun <T : Component> classMatcher(name: String, base: Class<T>): ClassNameMatcher<T> {
    return ClassNameMatcher.forClass(name, base)
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
    val panels = finder().findAll(contents[0].component, classMatcher("io.flutter.view.InspectorPanel", JPanel::class.java))
    return panels.firstOrNull { it is InspectorPanel && it.treeType == type && !it.isDetailsSubtree } as InspectorPanel
  }

  inner class InspectorPanelFixture(val inspectorPanel: InspectorPanel) {

    fun inspectorTreeFixture(): InspectorTreeFixture {
      val inspectorTreeRef = Ref<InspectorTree>()

      pause(object : Condition("Tree shows up") {
        override fun test(): Boolean {
          val inspectorTree = findInspectorTree()
          inspectorTreeRef.set(inspectorTree)
          return inspectorTree != null
        }
      }, Timeouts.seconds10)

      val inspectorTree = inspectorTreeRef.get()
      assertNotNull(inspectorTree)
      return InspectorTreeFixture(inspectorTree)
    }

    fun findInspectorTree(): InspectorTree? {
      val trees = finder().findAll(inspectorPanel, classMatcher("io.flutter.inspector.InspectorTree", JTree::class.java))
      val tree = trees.firstOrNull { it is InspectorTree && !it.detailsSubtree }
      if (tree != null) return tree as InspectorTree else return null
    }
  }

  inner class InspectorTreeFixture(val inspectorTree: InspectorTree) {

    fun treeFixture() : JTreeFixture {
      return JTreeFixture(ideFrame.robot(), inspectorTree)
    }

    fun selectRow(number: Int) {
      pause(object : Condition("Tree has content") {
        override fun test(): Boolean {
          return inspectorTree.rowCount > 0
        }
      }, Timeouts.seconds05)
//      val click = MouseEvent(inspectorTree, 0, 0L, 0, 100, 30, 1, false)
//      EdtInvocationManager.getInstance().invokeAndWait() {
//        inspectorTree.dispatchEvent(click)
//      }
      treeFixture().clickRow(number)
    }

    fun selection(): TreePath? {
      return inspectorTree.selectionPath
    }
  }
}
