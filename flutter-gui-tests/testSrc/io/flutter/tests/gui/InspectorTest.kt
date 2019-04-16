/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.tests.gui

import com.intellij.openapi.util.SystemInfo.isMac
import com.intellij.testGuiFramework.fixtures.ActionButtonFixture
import com.intellij.testGuiFramework.fixtures.ExecutionToolWindowFixture
import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.framework.RunWithIde
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.testGuiFramework.impl.button
import com.intellij.testGuiFramework.launcher.ide.CommunityIde
import com.intellij.testGuiFramework.util.step
import io.flutter.tests.gui.fixtures.FlutterInspectorFixture
import org.fest.swing.edt.GuiActionRunner.execute
import org.fest.swing.edt.GuiQuery
import org.fest.swing.fixture.JButtonFixture
import org.fest.swing.fixture.JTreeRowFixture
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause.pause
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.awt.Container
import java.awt.event.KeyEvent
import kotlin.test.expect
import kotlin.test.fail

@RunWithIde(CommunityIde::class)
class InspectorTest : GuiTestCase() {

  //@Test
  fun widgetTree() {
    ProjectCreator.importProject()
    ideFrame {
      launchFlutterApp()
      val inspector = flutterInspectorFixture(this)
      inspector.populate()
      val widgetTree = inspector.widgetTreeFixture()
      val inspectorTree = widgetTree.inspectorTreeFixture()
      val detailsTree = widgetTree.inspectorTreeFixture(isDetails = true)
      expect(true) { detailsTree.selection() == null }

      step("Details selection synced with main tree") {
        inspectorTree.selectRow(2, expand = true)
        expect("[[root], MyApp, MaterialApp, MyHomePage]") { inspectorTree.selectionSync().toString() }
        expect("[MyHomePage]") { detailsTree.selectionSync().toString() }
        inspectorTree.selectRow(10, expand = true)
        expect("[[root], MyApp, MaterialApp, MyHomePage, Scaffold, FloatingActionButton]") {
          inspectorTree.selectionSync().toString()
        }
        val string = detailsTree.selectionSync().toString()
        expect(true) {
          string.startsWith("[MyHomePage,") && string.endsWith("FloatingActionButton]")
        }
      }

      // This is disabled due to an issue in the test framework. The #selectRow call causes
      // the widget tree to change its selection, which is absolutely not what we want.
      //      step("Details selection leaves main tree unchanged") {
      //        val string = detailsTree.selectionSync().toString()
      //        detailsTree.selectRow(1, expand = false)
      //        pause(object : Condition("Details tree changes") {
      //          override fun test(): Boolean {
      //            return string != detailsTree.selectionSync().toString()
      //          }
      //        }, Timeouts.seconds05)
      //        expect("[[root], MyApp, MaterialApp, MyHomePage, Scaffold, FloatingActionButton]") {
      //          inspectorTree.selectionSync().toString()
      //        }
      //      }

      runner().stop()
    }
  }

  @Test
  fun hotReload() {
    ProjectCreator.importProject()
    ideFrame {
      launchFlutterApp()
      val inspector = flutterInspectorFixture(this)
      inspector.populate()
      var widgetTree = inspector.widgetTreeFixture()
      var inspectorTree = widgetTree.inspectorTreeFixture()
      inspectorTree.selectRow(0)
      var detailsTree = widgetTree.inspectorTreeFixture(isDetails = true)
      val initialDetails = detailsTree.selectionSync().toString()

      editor {
        // Wait until current file has appeared in current editor and set focus to editor.
        moveTo(0)
        val editorCode = getCurrentFileContents(false)!!
        val original = "pushed the button this"
        val index = editorCode.indexOf(original) + original.length
        moveTo(index)
        val key = if (isMac) KeyEvent.VK_BACK_SPACE else KeyEvent.VK_DELETE
        for (n in 1..4) typeKey(key)
        typeText("that")
        //typeKey(KeyEvent.VK_ESCAPE) // Dismiss completion popup -- not needed with "that" but is needed with "so"
      }

      step("Trigger Hot Reload and wait for it to finish") {
        val reload = findHotReloadButton()
        reload.click()
        editor.clickCenter() // Need to cycle the event loop to get button enabled on Mac.
        pause(object : Condition("Hot Reload finishes") {
          override fun test(): Boolean {
            return reload.isEnabled
          }
        }, Timeouts.seconds05)
        // Work around https://github.com/flutter/flutter-intellij/issues/3370
        inspector.renderTreeFixture() // The refresh button is broken so force tree update by switching views.
        inspector.populate()
        widgetTree = inspector.widgetTreeFixture() // And back to the one we want
        inspectorTree = widgetTree.inspectorTreeFixture()
        inspectorTree.selectRow(6)
        detailsTree = widgetTree.inspectorTreeFixture(isDetails = true)
        pause(object : Condition("Details tree changes") {
          override fun test(): Boolean {
            return initialDetails != detailsTree.selectionSync().toString()
          }
        }, Timeouts.seconds05)
        val row: JTreeRowFixture = detailsTree.treeFixture().node(1)
        val expected = "\"You have pushed the button that many times:\""
        expect(expected) { row.value() }
      }

      runner().stop()
    }
  }

  private fun findHotReloadButton(): ActionButtonFixture {
    return findActionButtonByClassName("ReloadFlutterAppRetarget")
  }

  private fun IdeFrameFixture.runner(): ExecutionToolWindowFixture.ContentFixture {
    return runToolWindow.findContent("main.dart")
  }

  fun IdeFrameFixture.launchFlutterApp() {
    step("Launch Flutter app") {
      findRunApplicationButton().click()
      val runner = runner()
      pause(object : Condition("Start app") {
        override fun test(): Boolean {
          return runner.isExecutionInProgress
        }
      }, Timeouts.seconds30)
    }
  }

  fun IdeFrameFixture.selectSimulator() {
    step("Select Simulator") {
      selectDevice("Open iOS Simulator")
    }
  }

  fun IdeFrameFixture.selectDevice(devName: String) {
    val runButton = findRunApplicationButton()
    val actionToolbarContainer = execute<Container>(object : GuiQuery<Container>() {
      @Throws(Throwable::class)
      override fun executeInEDT(): Container? {
        return runButton.target().parent
      }
    })
    assertNotNull(actionToolbarContainer)

    // These next two lines look like the right way to select the simulator, but it does not work.
    //    val comboBoxActionFixture = ComboBoxActionFixture.findComboBoxByText(robot(), actionToolbarContainer!!, "<no devices>")
    //    comboBoxActionFixture.selectItem(devName)
    // Need to get focus on the combo box but the ComboBoxActionFixture.click() method is private, so it is inlined here.
    val selector = button("<no devices>")
    val comboBoxButtonFixture = JButtonFixture(robot(), selector.target())
    GuiTestUtilKt.waitUntil("ComboBoxButton will be enabled", Timeouts.seconds10) {
      GuiTestUtilKt.computeOnEdt { comboBoxButtonFixture.target().isEnabled } ?: false
    }
    comboBoxButtonFixture.click()
    robot().pressAndReleaseKey(KeyEvent.VK_DOWN)
    robot().pressAndReleaseKey(KeyEvent.VK_ENTER)
    robot().waitForIdle()
  }

  private fun findActionButtonByActionId(actionId: String): ActionButtonFixture {
    // This seems to be broken, but finding by simple class name works.
    var button: ActionButtonFixture? = null
    ideFrame {
      button = ActionButtonFixture.fixtureByActionId(target().parent, robot(), actionId)
    }
    return button!!
  }

  private fun findActionButtonByClassName(className: String): ActionButtonFixture {
    var button: ActionButtonFixture? = null
    ideFrame {
      button = ActionButtonFixture.fixtureByActionClassName(target(), robot(), className)
    }
    return button!!
  }

  fun IdeFrameFixture.flutterInspectorFixture(ideFrame: IdeFrameFixture): FlutterInspectorFixture {
    return FlutterInspectorFixture(project, robot(), ideFrame)
  }

}
