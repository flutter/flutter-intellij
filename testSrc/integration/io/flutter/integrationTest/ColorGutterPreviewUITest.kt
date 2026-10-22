/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import io.flutter.integrationTest.utils.gutterColors
import io.flutter.integrationTest.utils.toHexColor
import org.junit.jupiter.api.Test
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.minutes

// TODO(radcliffc): `Colors.blue` directly in a variable initializer (`final c = Colors.blue;`) gets no gutter preview:
//  FlutterColorProvider.getColorFrom skips the `Colors` token in a VAR_INIT (to avoid a duplicate) and then can't
//  resolve `blue`. `Colors.green.shade700`, `Color(...)`, and `Color.fromARGB(...)` in initializers do work. The test
//  uses a list literal instead; decide whether this is a plugin bug to fix and test.
/**
 * **Shows:** color previews in the editor gutter for Flutter color expressions, and that they keep working while you
 * edit around them (TestTracker test case 1.2.4, "Color Gutter Previews").
 *
 * Steps:
 * 1. IntelliJ IDEA opens an existing Flutter app (created with `flutter create`) and `lib/main.dart`.
 * 2. The gutter shows a deep purple square next to `seedColor: Colors.deepPurple`.
 * 3. Add a list of four colors at the end of `lib/main.dart`: `Colors.blue`, `Colors.green.shade700`,
 *    `Color(0xFFFF9800)` (orange), and `Color.fromARGB(255, 0, 150, 136)` (teal). A matching square appears next to
 *    each one.
 * 4. Type incomplete color expressions (`Color.fromARGB(255, 25`, `Color(0x`, `Colors.`) and delete them again.
 * 5. Change `Colors.blue` to `Colors.red`. The blue square turns red.
 *
 * Expected result: every color line has a square of the right color, the squares follow edits, and the IDE log has
 * no `IndexOutOfBoundsException` from the Flutter plugin.
 *
 * Implementation notes:
 * The test reads the gutter icons through the Driver (`gutterColors()`) and compares their RGB values, so it checks
 * the actual colors, not just that some icon is there. At the end it scans this run's `idea.log` for
 * `IndexOutOfBoundsException` entries that mention `io.flutter` (earlier bugs: #6942, #9091).
 */
class ColorGutterPreviewUITest : ExistingProjectUITest("color_gutter") {

  @Test
  fun colorPreviewsInGutter() {
    startIdeOnProject()
    step("Open lib/main.dart", "The editor shows the counter app's `lib/main.dart`; a deep purple square appears in the gutter next to `seedColor: Colors.deepPurple`.")
    openProjectFile("lib/main.dart")

    inFrame {
      val editor = editorFor("main.dart")
      editor.moveCaretToText("Colors.deepPurple")
      editor.scrollToCaret()
      editor.waitForGutterColors(mapOf(editor.lineOf("Colors.deepPurple") to DEEP_PURPLE))

      step(
        "Add colors",
        "A `testColors` list with four colors is added at the end of `lib/main.dart`. Next to each, the gutter shows a square in its color: blue, dark green, orange, and teal."
      )
      editor.text = editor.text.trimEnd() + "\n\n" + ADDED_COLORS
      editor.moveCaretToText("Color.fromARGB(255, 0, 150, 136)")
      editor.scrollToCaret()
      editor.waitForGutterColors(ADDED_COLOR_VALUES.mapKeys { (name, _) -> editor.lineOf(name) })

      step(
        "Type incomplete color code",
        "Below the new colors, `Color.fromARGB(255, 25`, `Color(0x`, and `Colors.` are typed one character at a time and deleted again. The existing squares stay, and no error notification appears."
      )
      for (partial in INCOMPLETE_COLORS) {
        typeOnNewLastLine(editor, partial)
      }

      step("Change blue to red", "In the list, `Colors.blue` becomes `Colors.red`; the square next to it turns from blue to red.")
      val blueStart = editor.text.indexOf("Colors.blue,") + "Colors.".length
      editor.setSelection(blueStart, blueStart + "blue".length)
      keyboard { typeText("red", 80) }
      Thread.sleep(500)
      keyboard { escape() }
      editor.waitForGutterColors(mapOf(editor.lineOf("Colors.red,") to RED))
    }

    checkIdeLogForFlutterIndexOutOfBounds()
  }

  /** Types [code] character by character on a new last line, gives code analysis time to run, then deletes the line. */
  private fun IdeaFrameUI.typeOnNewLastLine(editor: JEditorUiComponent, code: String) {
    editor.click()
    if (!editor.text.endsWith("\n")) {
      editor.moveCaretToOffset(editor.text.length)
      keyboard { enter() }
    }
    editor.moveCaretToOffset(editor.text.length)
    keyboard {
      typeText(code, 120)
    }
    Thread.sleep(3_000)
    keyboard { escape() } // Close a completion popup, if any.
    // Not Ctrl+Y: IntelliJ 2026.2 asks whether Ctrl+Y should mean Redo or Delete Line the first time it's pressed.
    driver.invokeAction("EditorDeleteLine", component = editor.component)
    Thread.sleep(1_000)
  }

  /** Waits until the gutter shows each color in [expected] (0-based line to `0xRRGGBB`). */
  private fun JEditorUiComponent.waitForGutterColors(expected: Map<Int, Int>) {
    var actual = emptyMap<Int, List<Int>>()
    try {
      waitUntil("gutter color icons ${expected.describe()}", timeout = 2.minutes) {
        actual = runCatching { gutterColors() }.getOrDefault(emptyMap())
        expected.all { (line, color) -> actual[line]?.contains(color) == true }
      }
    }
    catch (e: Exception) {
      throw AssertionError("Expected gutter colors ${expected.describe()}, but the gutter shows ${actual.mapValues { (_, c) -> c.map { it.toHexColor() } }}", e)
    }
    println("Gutter colors: ${expected.describe()}")
  }

  private fun Map<Int, Int>.describe() = entries.joinToString { (line, color) -> "line ${line + 1}: ${color.toHexColor()}" }

  /** The 0-based line of the first occurrence of [text]. */
  private fun JEditorUiComponent.lineOf(text: String): Int {
    val content = this.text
    val offset = content.indexOf(text)
    check(offset >= 0) { "'$text' not found in the editor" }
    return content.substring(0, offset).count { it == '\n' }
  }

  /** Fails if this run's IDE log has an `IndexOutOfBoundsException` entry that involves the Flutter plugin. */
  private fun checkIdeLogForFlutterIndexOutOfBounds() {
    val log = ideLogFile
    check(log.exists()) { "IDE log not found: $log" }
    // A log entry starts with a timestamp; stack traces follow on the next lines.
    val entries = log.readText().split(Regex("\n(?=\\d{4}-\\d{2}-\\d{2} )"))
    val outOfBounds = entries.filter { it.contains("IndexOutOfBoundsException") }
    val fromFlutter = outOfBounds.filter { it.contains("io.flutter") }
    if (outOfBounds.size > fromFlutter.size) {
      println("Note: ${outOfBounds.size - fromFlutter.size} IndexOutOfBoundsException entries in $log not involving io.flutter")
    }
    if (fromFlutter.isNotEmpty()) {
      throw AssertionError("The IDE log has ${fromFlutter.size} IndexOutOfBoundsException entries from the Flutter plugin:\n" +
                           fromFlutter.first().lines().take(15).joinToString("\n"))
    }
    println("No IndexOutOfBoundsException from the Flutter plugin in $log")
  }

  private companion object {
    const val DEEP_PURPLE = 0x673AB7
    const val RED = 0xF44336

    // A list, like colors passed to widgets. (`final c = Colors.blue;` gets no preview; see the class TODO.)
    const val ADDED_COLORS = """// Added by ColorGutterPreviewUITest.
final testColors = <Color>[
  Colors.blue,
  Colors.green.shade700,
  Color(0xFFFF9800),
  Color.fromARGB(255, 0, 150, 136),
];
"""

    /** Expected color for each added line, keyed by the code on that line. */
    val ADDED_COLOR_VALUES = mapOf(
      "Colors.blue," to 0x2196F3,
      "Colors.green.shade700" to 0x388E3C,
      "Color(0xFFFF9800)" to 0xFF9800,
      "Color.fromARGB(255, 0, 150, 136)" to 0x009688,
    )

    val INCOMPLETE_COLORS = listOf(
      "final testPartial = Color.fromARGB(255, 25",
      "final testHex = Color(0x",
      "final testDot = Colors.",
    )
  }
}
