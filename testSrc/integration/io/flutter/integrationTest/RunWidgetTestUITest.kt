/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.driver.model.RemoteMouseButton
import org.junit.jupiter.api.Test
import java.awt.event.KeyEvent
import kotlin.time.Duration.Companion.minutes

/**
 * Runs the generated widget test from the editor ("Run context configuration", Ctrl+Shift+F10) and waits for the
 * Flutter test runner to report success. Needs no device.
 */
class RunWidgetTestUITest : ExistingProjectUITest("run_widget_test") {

  @Test
  fun runWidgetTestFromEditor() {
    startIdeOnProject()
    openProjectFile("test/widget_test.dart")

    inFrame {
      val editor = editorFor("widget_test.dart")
      editor.clickOn("testWidgets", RemoteMouseButton.LEFT, 1)
      keyboard { hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_F10) }

      // The test runner status reads e.g. "1 test passed" or "3 tests passed".
      waitUntil("widget test to pass", timeout = 5.minutes) {
        x("//div[contains(@visible_text, 'test passed') or contains(@visible_text, 'tests passed')]").present() ||
        allEditorText().contains("All tests passed")
      }
    }
  }
}
