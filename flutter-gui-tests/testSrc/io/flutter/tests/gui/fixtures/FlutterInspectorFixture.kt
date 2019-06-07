/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.tests.gui.fixtures

import com.intellij.execution.ui.layout.impl.JBRunnerTabs
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.fixtures.JComponentFixture
import com.intellij.testGuiFramework.fixtures.ToolWindowFixture
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.GuiRobotHolder.robot
import com.intellij.testGuiFramework.matcher.ClassNameMatcher
import com.intellij.testGuiFramework.util.step
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.newImpl.TabLabel
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
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.TreePath

fun IdeFrameFixture.flutterInspectorFixture(ideFrame: IdeFrameFixture): FlutterInspectorFixture {
  return FlutterInspectorFixture(project, robot(), ideFrame)
}

// A fixture for the Inspector top-level view.
class FlutterInspectorFixture(project: Project, robot: Robot, private val ideFrame: IdeFrameFixture)
  : ToolWindowFixture("Flutter Inspector", project, robot) {

  fun populate() {
    step("Populate inspector tree") {
      activate()
      selectedContent
      Pause.pause(object : Condition("Initialize inspector") {
        override fun test(): Boolean {
          return contents[0].displayName != null
        }
      }, Timeouts.seconds30)
    }
  }

  fun widgetsFixture(): InspectorPanelFixture {
    showTab(0)
    return inspectorPanel(InspectorService.FlutterTreeType.widget)
  }

  fun renderTreeFixture(): InspectorPanelFixture {
    showTab(1)
    return inspectorPanel(InspectorService.FlutterTreeType.renderObject)
  }

  private fun showTab(index: Int) {
    val tabs: JBRunnerTabs = contents[0].component.components[0] as JBRunnerTabs
    val info: TabInfo = tabs.getTabAt(index)
    val label = tabs.getTabLabel(info)
    TabLabelFixture(robot, label).click()
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
    return InspectorPanelFixture(notificationPanel, type)
  }

  private fun findInspectorPanel(type: InspectorService.FlutterTreeType): InspectorPanel? {
    val panels = finder().findAll(contents[0].component, classMatcher("io.flutter.view.InspectorPanel", JPanel::class.java))
    return panels.firstOrNull { it is InspectorPanel && it.treeType == type && !it.isDetailsSubtree } as InspectorPanel
  }

  // The InspectorPanel is a little tricky to work with. In order for the tree to have content its view must be made
  // visible (either Widgets or Render Tree). However, we don't want to return a reference to an empty tree, because
  // of timing issues. We use the fact that the tree has content as a signal to move on to the next step.
  inner class InspectorPanelFixture(val inspectorPanel: InspectorPanel, val type: InspectorService.FlutterTreeType) {

    fun show() {
      showTab(tabIndex())
    }

    private fun tabIndex(): Int {
      return if (type == InspectorService.FlutterTreeType.widget) 0 else 1
    }

    fun inspectorTreeFixture(isDetails: Boolean = false): InspectorTreeFixture {
      val inspectorTreeRef = Ref<InspectorTree>()

      pause(object : Condition("Tree shows up") {
        override fun test(): Boolean {
          val inspectorTree = findInspectorTree(isDetails)
          inspectorTreeRef.set(inspectorTree)
          return inspectorTree != null
        }
      }, Timeouts.seconds10)

      val inspectorTree = inspectorTreeRef.get()
      assertNotNull(inspectorTree)
      return InspectorTreeFixture(inspectorTree)
    }

    fun findInspectorTree(isDetails: Boolean): InspectorTree? {
      val trees = finder().findAll(inspectorPanel, classMatcher("io.flutter.inspector.InspectorTree", JTree::class.java))
      val tree = trees.firstOrNull { it is InspectorTree && isDetails == it.detailsSubtree }
      if (tree != null) return tree as InspectorTree else return null
    }
  }

  // This fixture is used to access the tree for both Widgets and Render Objects.
  inner class InspectorTreeFixture(private val inspectorTree: InspectorTree) {

    fun treeFixture(): JTreeFixture {
      return JTreeFixture(ideFrame.robot(), inspectorTree)
    }

    fun selectRow(number: Int, expand: Boolean = true) {
      waitForContent()
      treeFixture().clickRow(number) // This should not collapse the tree, but it does.
      if (expand) {
        treeFixture().expandRow(number) // TODO(messick) Remove when selection preserves tree expansion.
      }
    }

    fun selection(): TreePath? {
      return inspectorTree.selectionPath
    }

    fun selectionSync(): TreePath {
      waitForContent()
      waitForCondition("Selection is set") { inspectorTree.selectionPath != null }
      return selection()!!
    }

    private fun waitForContent() {
      waitForCondition("Tree has content") { inspectorTree.rowCount > 1 }
    }

    private fun waitForCondition(description: String, condition: () -> Boolean): Boolean {
      var result: Boolean = false
      pause(object : Condition(description) {
        override fun test(): Boolean {
          result = condition.invoke()
          return result
        }
      }, Timeouts.seconds05)
      return result
    }
  }
}

// A clickable fixture for the three tabs in the inspector: Widgets, Render Tree, and Performance.
class TabLabelFixture(robot: Robot, target: TabLabel)
  : JComponentFixture<TabLabelFixture, TabLabel>(TabLabelFixture::class.java, robot, target)
