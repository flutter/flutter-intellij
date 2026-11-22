/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.driver.sdk.ActionManager
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.toolwindows.projectView
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Checks that the UI test harness itself works: the IDE starts on a `flutter create` project, the Flutter plugin is
 * loaded and its actions are registered, a file opens in the editor, and the device selector is on the toolbar.
 *
 * This is the harness's own smoke test and the first target of the plugin-off control: with
 * `FLUTTER_UI_TEST_WITHOUT_PLUGIN=true` it must fail at `O2`, because none of the `Flutter.*` actions exist. It is not a
 * workflow spec test; it has no entry in the spec catalog.
 */
@Tag("harness")
class HarnessSelfCheckUITest : ExistingProjectUITest("harness_check") {

  @Test
  fun pluginLoadsAndWiresItsUi() {
    startIdeOnProject()

    assertOracle("O1", "the project opened: the Project view lists pubspec.yaml and indexing finished") {
      inFrame { projectView { projectViewTree.waitFound().pathExists("harness_check", "pubspec.yaml") } }
    }

    step("Open lib/main.dart from the Project view") {
      openProjectFile("lib/main.dart")
    }

    assertOracle("O2", "the Flutter plugin registered its actions (hot reload, pub get) with the IDE") {
      val actions = driver.service(ActionManager::class)
      for (id in listOf("Flutter.Toolbar.ReloadAction", "Flutter.Toolbar.RestartAction", "flutter.pub.get")) {
        assertNotNull(actions.getAction(id), "action '$id' is not registered; is the Flutter plugin loaded?")
      }
    }

    assertOracle("O3", "main.dart is open in the editor and the Flutter device selector is on the toolbar") {
      inFrame {
        assertTrue(editorFor("main.dart").text.contains("MaterialApp"), "main.dart editor does not show the Flutter template")
        waitUntil("device selector to appear on the toolbar") {
          xx("//div[contains(@javaclass, 'DeviceSelectorAction')]").list().isNotEmpty()
        }
      }
    }

    assertOracle("O4", "no console errors; idea.log and process checks run in teardown") {
      inFrame {
        val console = consoleText()
        assertTrue(!console.contains("Exception") && !console.contains("Error:"), "a console shows an error: $console")
      }
    }
  }
}
