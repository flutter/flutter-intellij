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
 * **Shows:** the Flutter plugin's **Extract Flutter Widget...** refactoring turning a widget expression into its
 * own `StatelessWidget` class.
 *
 * Steps:
 * 1. IntelliJ IDEA opens an existing Flutter app (created with `flutter create`).
 * 2. Open `lib/main.dart` and put the caret on `Text('You have pushed the button this many times:')`.
 * 3. Run **Extract Flutter Widget...**. A dialog asks for the widget name (default `NewWidget`).
 * 4. Type `CounterLabel` and press Enter. The Dart analysis server performs the refactoring.
 *
 * Expected result: `lib/main.dart` contains a new `class CounterLabel extends StatelessWidget`, and the original
 * `Text(...)` is replaced by `CounterLabel()`.
 */
class ExtractWidgetUITest : ExistingProjectUITest("extract_widget_test") {

  @Test
  fun extractTextIntoWidget() {
    startIdeOnProject()
    step("Open lib/main.dart", "The editor shows the counter app's `lib/main.dart`.")
    openProjectFile("lib/main.dart")

    inFrame {
      val editor = editorFor("main.dart")
      // Focus the editor, then put the caret on the `Text('You have pushed ...')` widget creation. Moving the caret
      // programmatically works even when that line is scrolled out of view.
      step("Place the caret on the Text widget", "The editor scrolls to `const Text('You have pushed the button this many times:')` with the caret on `Text`.")
      editor.click()
      editor.moveCaretToText("Text('You have pushed")
      editor.scrollToCaret()
      Thread.sleep(1_000)

      step("Run Extract Flutter Widget", "The \"Extract Widget\" dialog opens with the name field showing `NewWidget` selected.")
      // Invoke "Extract Flutter Widget..." with the editor as the action's context.
      driver.invokeAction("Flutter.ExtractWidget", now = false, component = editor.component)

      // The dialog opens with the name field focused and "NewWidget" selected.
      x("//div[@class='JTextField' and @visible_text='NewWidget']").waitFound(30.seconds)
      step("Name the widget CounterLabel", "`CounterLabel` replaces `NewWidget` in the dialog; pressing Enter runs the refactoring.")
      keyboard {
        typeText("CounterLabel", 60)
        enter()
      }

      step(
        "Check the extracted widget",
        "The dialog closes; the `Text(...)` line now reads `const CounterLabel()`, and a new `class CounterLabel extends StatelessWidget` appears at the end of the file."
      )
      waitUntil("main.dart to contain the extracted widget") {
        val text = editor.text
        text.contains("class CounterLabel extends StatelessWidget") && text.contains("CounterLabel()")
      }
      // Show the new class in the editor for the recording.
      editor.moveCaretToText("class CounterLabel")
      editor.scrollToCaret()
      Thread.sleep(2_000)
    }
  }
}
