/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.tests.gui

import com.intellij.openapi.util.SystemInfo.isMac
import com.intellij.testGuiFramework.fixtures.ActionButtonFixture
import com.intellij.testGuiFramework.framework.RunWithIde
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.launcher.ide.CommunityIde
import com.intellij.testGuiFramework.util.step
import io.flutter.tests.gui.fixtures.flutterInspectorFixture
import org.fest.swing.fixture.JTreeRowFixture
import org.fest.swing.timing.Condition
import org.fest.swing.timing.Pause.pause
import org.junit.Test
import java.awt.event.KeyEvent
import kotlin.test.expect

@RunWithIde(CommunityIde::class)
class InspectorTest : GuiTestCase() {

  @Test
  fun widgetTree() {
    ProjectCreator.importProject()
    ideFrame {
      launchFlutterApp()
      val inspector = flutterInspectorFixture(this)
      inspector.populate()
      val widgetTree = inspector.widgetsFixture()
      val inspectorTree = widgetTree.inspectorTreeFixture()
      val detailsTree = widgetTree.inspectorTreeFixture(isDetails = true)
      expect(true) { detailsTree.selection() == null }

      step("Details selection synced with main tree") {
        inspectorTree.selectRow(2, reexpand = true)
        expect("[[root], MyApp, MaterialApp, MyHomePage]") { inspectorTree.selectionSync().toString() }
        expect("[MyHomePage]") { detailsTree.selectionSync().toString() }
        inspectorTree.selectRow(10, reexpand = true)
        expect("[[root], MyApp, MaterialApp, MyHomePage, Scaffold, FloatingActionButton]") {
          inspectorTree.selectionSync().toString()
        }
        val string = detailsTree.selectionSync().toString()
        expect(true) {
          string.startsWith("[FloatingActionButton]")
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
      val widgets = inspector.widgetsFixture()
      val widgetsTree = widgets.inspectorTreeFixture(isDetails = false)
      widgetsTree.selectRow(0)
      val detailsTree = widgets.inspectorTreeFixture(isDetails = true)
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
        step("Work around #3370") {
          // https://github.com/flutter/flutter-intellij/issues/3370
          inspector.renderTreeFixture().show() // The refresh button is broken so force tree update by switching views.
          inspector.populate()
          widgets.show() // And back to the one we want
        }
        widgetsTree.selectRow(6) // Text widget
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

  private fun findActionButtonByActionId(actionId: String): ActionButtonFixture {
    // This seems to be broken, but finding by simple class name works.
    var button: ActionButtonFixture? = null
    ideFrame {
      button = ActionButtonFixture.fixtureByActionId(target().parent, robot(), actionId)
    }
    return button!!
  }

  private fun findActionButtonByClassName(className: String): ActionButtonFixture {
    // This works when the button is enabled but fails if it is disabled (implementation detail).
    var button: ActionButtonFixture? = null
    ideFrame {
      button = ActionButtonFixture.fixtureByActionClassName(target(), robot(), className)
    }
    return button!!
  }

}
