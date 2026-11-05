/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.driver.model.RemoteMouseButton
import com.intellij.driver.sdk.invokeAction
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

/**
 * **Shows:** running a Flutter widget test from the editor and seeing the result in the IDE's test runner.
 *
 * Steps:
 * 1. IntelliJ IDEA opens an existing Flutter app (created with `flutter create`).
 * 2. Open `test/widget_test.dart` and put the caret on `testWidgets`.
 * 3. Run Context Configuration (Ctrl+Shift+F10, or ⌃⇧R on macOS). The plugin creates a Flutter test run
 *    configuration and runs `flutter test` for that test.
 * 4. The Run tool window shows the test tree and progress.
 *
 * Expected result: the test runner reports "1 test passed" for the "Counter increments smoke test". No device or
 * emulator is needed.
 */
class RunWidgetTestUITest : ExistingProjectUITest("run_widget_test") {

  @Test
  fun runWidgetTestFromEditor() {
    startIdeOnProject()
    step("Open the widget test", "The editor shows `test/widget_test.dart` with the \"Counter increments smoke test\" and a green run icon in the gutter next to `testWidgets`.")
    openProjectFile("test/widget_test.dart")

    inFrame {
      val editor = editorFor("widget_test.dart")
      step("Run the test from the editor", "The caret is on `testWidgets`; Run Context Configuration (Ctrl+Shift+F10, or ⌃⇧R on macOS) starts a Flutter test run configuration named after the test.")
      editor.clickOn("testWidgets", RemoteMouseButton.LEFT, 1)
      // Invoke the action by ID rather than pressing its shortcut, which differs between keymaps (macOS vs. others).
      driver.invokeAction("RunClass", now = false, component = editor.component)

      step(
        "Watch the test runner",
        "The Run tool window shows the test tree and the `flutter test` command; the status changes from \"Instantiating tests\" to \"1 test passed\" with a green check."
      )
      // The test runner status reads e.g. "1 test passed" or "3 tests passed".
      waitUntil("widget test to pass", timeout = 5.minutes) {
        x("//div[contains(@visible_text, 'test passed') or contains(@visible_text, 'tests passed')]").present() ||
        allEditorText().contains("All tests passed")
      }
    }
  }
}
