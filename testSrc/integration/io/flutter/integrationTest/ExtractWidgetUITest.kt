/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import org.junit.jupiter.api.Test
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import kotlin.time.Duration.Companion.seconds

/**
 * Uses the plugin's "Extract Flutter Widget..." refactoring on a `Text` widget in `lib/main.dart`.
 */
class ExtractWidgetUITest : ExistingProjectUITest("extract_widget_test") {

  @Test
  fun extractTextIntoWidget() {
    startIdeOnProject()
    openProjectFile("lib/main.dart")

    inFrame {
      val editor = editorFor("main.dart")
      // Focus the editor, then put the caret on the `Text('You have pushed ...')` widget creation. Moving the caret
      // programmatically works even when that line is scrolled out of view.
      editor.click()
      editor.moveCaretToText("Text('You have pushed")
      Thread.sleep(1_000)

      // Invoke "Extract Flutter Widget..." with the editor as the action's context.
      driver.invokeAction("Flutter.ExtractWidget", now = false, component = editor.component)

      // The dialog opens with the name field focused and "NewWidget" selected.
      x("//div[@class='JTextField' and @visible_text='NewWidget']").waitFound(30.seconds)
      keyboard {
        typeText("CounterLabel", 60)
        enter()
      }

      waitUntil("main.dart to contain the extracted widget") {
        val text = editor.text
        text.contains("class CounterLabel extends StatelessWidget") && text.contains("CounterLabel()")
      }
    }
  }
}
