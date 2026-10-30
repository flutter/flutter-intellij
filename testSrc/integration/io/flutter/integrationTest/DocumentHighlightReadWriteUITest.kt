/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import io.flutter.integrationTest.utils.UsageAccess
import io.flutter.integrationTest.utils.checkDartSdkAtLeast
import io.flutter.integrationTest.utils.dartExperimentalLspFeaturesEnabled
import io.flutter.integrationTest.utils.setDartExperimentalLspFeatures
import io.flutter.integrationTest.utils.UsageHighlight
import io.flutter.integrationTest.utils.usageHighlights
import org.junit.jupiter.api.Test
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

/**
 * **Shows:** the Dart plugin highlighting the occurrences of a variable under the caret, with writes and reads in
 * different colors, while "Turn on experimental LSP features" is off (Dart plugin TestTracker plan 157229, case 1.2.5,
 * "Document Highlight (Read vs. Write Occurrences) Enabled by Default"; Dart plugin 510.0.0, #693).
 *
 * Steps:
 * 1. IntelliJ IDEA opens an existing Flutter app (created with `flutter create`) with this `lib/highlight_sample.dart`:
 *    ```
 *    void test() {
 *      var count = 0;
 *      count = count + 1;
 *      print(count);
 *    }
 *    ```
 * 2. In **Settings > Languages & Frameworks > Dart**, turn off **Turn on experimental LSP features** and click
 *    **Apply** and **OK**.
 * 3. Open `lib/highlight_sample.dart` and put the caret on `count` in `var count = 0`.
 *
 * Expected result: all four occurrences of `count` are highlighted. The two writes (`var count = 0` and `count =`)
 * use the write-access highlight color; the two reads (`count + 1` and `print(count)`) use the read-access color.
 *
 * Implementation notes:
 * - Installs Dart plugin 510.0.0, the first version where read/write highlighting via LSP is on for all users.
 * - Needs a newer Dart SDK than the release note says: with Dart SDK 3.12.2 (Flutter 3.44.7) every occurrence is
 *   highlighted as a read; with 3.14.0-315.3.beta (Flutter 3.50 beta) writes and reads are told apart (2026-10-30).
 *   The test checks `FLUTTER_SDK`'s Dart SDK is 3.14 or newer before it starts the IDE. The exact first version that
 *   works wasn't narrowed down.
 * - The test reads the highlighters in the editor's markup model and checks, for each occurrence, its exact text
 *   range and its text attributes key (`WRITE_IDENTIFIER_UNDER_CARET_ATTRIBUTES` for writes,
 *   `IDENTIFIER_UNDER_CARET_ATTRIBUTES` for reads), so it fails if an occurrence is missing, extra, or has the wrong
 *   access kind. It also checks that the experimental flag (project property `dart.lsp.experimental.enabled`,
 *   default on) really is off before checking the highlights.
 * - The test case's last step, turning the flag back on, isn't needed: the test project is deleted afterwards.
 */
class DocumentHighlightReadWriteUITest : ExistingProjectUITest("document_highlight") {

  override val dartPluginVersion = "510.0.0"

  override fun prepareProject() {
    projectDir.resolve(SAMPLE_PATH).writeText(SAMPLE)
  }

  @Test
  fun readAndWriteOccurrencesAreHighlightedDifferently() {
    checkDartSdkAtLeast(MIN_SDK, "Read/write occurrence highlighting via LSP")
    startIdeOnProject()

    inFrame {
      step(
        "Turn off experimental LSP features",
        "Settings > Languages & Frameworks > Dart opens; \"Turn on experimental LSP features\" is checked by default, gets unchecked, and the change is applied."
      )
      setDartExperimentalLspFeatures(enabled = false)
    }
    check(!driver.dartExperimentalLspFeaturesEnabled()) { "\"Turn on experimental LSP features\" is still on after unchecking it in Settings" }
    println("Experimental LSP features: off")

    step("Open the sample file", "The editor shows `lib/highlight_sample.dart` with the `test()` function that writes and reads `count`.")
    openProjectFile(SAMPLE_PATH)

    inFrame {
      val editor = editorFor("highlight_sample.dart")
      val occurrences = Regex("\\bcount\\b").findAll(editor.text).map { it.range.first until it.range.last + 1 }.toList()
      check(occurrences.size == 4) { "Expected 4 occurrences of `count` in the sample, found ${occurrences.size}" }
      val expected = occurrences.zip(EXPECTED_ACCESS) { range, access -> UsageHighlight(range.first, range.last + 1, access) }

      step(
        "Put the caret on count",
        "With the caret on `count` in `var count = 0`, all four occurrences are highlighted: `var count = 0` and `count =` in the write color, `count + 1` and `print(count)` in the read color."
      )
      editor.click()
      val declaration = occurrences[0].first + 2 // Inside the first `count`.
      editor.moveCaretToOffset(declaration)

      var actual = emptyList<UsageHighlight>()
      try {
        waitUntil("read and write occurrences of `count` to be highlighted", timeout = 2.minutes) {
          actual = editor.usageHighlights()
          if (actual != expected) {
            // Highlighting runs after the Dart analysis server (LSP) is ready; move the caret away and back to re-run it.
            editor.moveCaretToOffset(0)
            Thread.sleep(500)
            editor.moveCaretToOffset(declaration)
          }
          actual == expected
        }
      }
      catch (e: Exception) {
        throw AssertionError(
          "Expected the occurrences of `count` to be highlighted as\n  ${expected.describe(editor)}\nbut the editor shows\n  ${actual.describe(editor)}", e
        )
      }
      println("Highlighted: ${actual.describe(editor)}")
    }
  }

  private fun List<UsageHighlight>.describe(editor: JEditorUiComponent): String {
    if (isEmpty()) return "(nothing highlighted)"
    val text = editor.text
    return joinToString("\n  ") { h ->
      val line = text.substring(0, h.start).count { it == '\n' }
      "${h.access} `${text.lines()[line].trim()}` at ${h.start}..${h.end}"
    }
  }

  private companion object {
    const val SAMPLE_PATH = "lib/highlight_sample.dart"

    /** Oldest Dart SDK seen to report writes (any 3.14 prerelease or later); 3.12.2 reports every occurrence as a read. */
    const val MIN_SDK = "3.14.0-0.0.dev"
    const val SAMPLE = """void test() {
  var count = 0;
  count = count + 1;
  print(count);
}
"""

    /** In order of appearance: `var count = 0`, `count =`, `count + 1`, `print(count)`. */
    val EXPECTED_ACCESS = listOf(UsageAccess.WRITE, UsageAccess.WRITE, UsageAccess.READ, UsageAccess.READ)
  }
}
