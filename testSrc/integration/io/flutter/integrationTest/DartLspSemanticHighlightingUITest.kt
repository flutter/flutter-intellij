/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import io.flutter.integrationTest.utils.AttributesHighlight
import io.flutter.integrationTest.utils.DartAnalysisServerServiceStatics
import io.flutter.integrationTest.utils.Platform
import io.flutter.integrationTest.utils.attributesHighlights
import io.flutter.integrationTest.utils.dartExperimentalLspFeaturesEnabled
import io.flutter.integrationTest.utils.setDartExperimentalLspFeatures
import org.junit.jupiter.api.Test
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.minutes

// TODO(radcliffc): Findings on 2026-10-29 (PR #640 merged with dart-intellij-third-party main, Dart SDK
//  3.14.0-315.3.beta): the 20 checked identifier colors, the edit, and the fallback all pass, but LSP highlighting
//  differs from legacy in 4 places, so the test fails:
//  - Constructor name `Counter(this.value)`: legacy DART_CLASS, LSP DART_CONSTRUCTOR (maybe intended).
//  - Writes `instances = 0` and `value += by`: legacy *_SETTER_REFERENCE, LSP *_GETTER_REFERENCE (no setter mapping).
//  - String escape `\t`: highlighted twice in LSP mode (DART_VALID_STRING_ESCAPE from LSP and from the annotator).
/**
 * **Shows:** Dart semantic syntax highlighting moving from the analysis server's legacy `analysis.highlights`
 * notifications to LSP semantic tokens when "Turn on experimental LSP features" is on, and falling back to legacy
 * highlighting when it's turned off ([dart-intellij-third-party#640](https://github.com/flutter/dart-intellij-third-party/pull/640),
 * fixing #401).
 *
 * Steps:
 * 1. IntelliJ IDEA opens an existing Flutter app with `lib/highlighting_sample.dart`, a file that uses classes, enums,
 *    type parameters, fields, getters, static and instance methods, parameters, local variables, top-level functions,
 *    an import prefix, a label, and a string escape.
 * 2. Turn **off** "Turn on experimental LSP features" (Settings > Languages & Frameworks > Dart). The file is
 *    highlighted the legacy way; this is the reference.
 * 3. Turn it **on**. The same file is now highlighted from LSP semantic tokens, and must look the same.
 * 4. Add a function `triple(int y)` to the file. Its name and parameter are highlighted right away.
 * 5. Turn the flag **off** again. Highlighting falls back to legacy, including the new function.
 *
 * Expected result: with LSP highlighting each identifier gets the same color as with legacy highlighting (class, enum,
 * method declaration vs. reference, static vs. instance, parameter, local variable, and so on), nothing is highlighted
 * twice, edits are highlighted, turning the flag off restores legacy highlighting, and the IDE logs no errors from the
 * Dart plugin.
 *
 * Implementation notes:
 * - Needs a Dart plugin with #640 (`DART_PLUGIN_ZIP`, for example `third_party/build/distributions/Dart-*.zip` built
 *   from the pull request) and a Dart SDK 3.14.0-307.0.dev or newer (`FLUTTER_SDK` pointing at a Flutter SDK that has
 *   one, for example the 3.50 beta). The test checks both first and stops with a clear message otherwise.
 * - Colors are checked as the text attributes keys of the editor's highlighters (`DART_CLASS`,
 *   `DART_INSTANCE_METHOD_DECLARATION`, ...), which is what the color scheme turns into colors. Which mechanism is
 *   active is checked with `DartAnalysisServerService.isLspHighlightingActive(project)`, since both produce the same keys.
 * - Lexer highlighting (comments, strings, numbers, built-in keywords) isn't part of either mechanism and isn't checked;
 *   neither are diagnostics (`DART_ERROR`, `DART_WARNING`, `DART_HINT` underlines).
 * - Differences between LSP and legacy highlighting don't stop the test; they're reported together at the end.
 */
class DartLspSemanticHighlightingUITest : ExistingProjectUITest("lsp_highlighting") {

  override fun prepareProject() {
    projectDir.resolve(SAMPLE_PATH).writeText(SAMPLE)
  }

  @Test
  fun lspHighlightingMatchesLegacyAndFallsBack() {
    checkDartSdkSupportsLspHighlighting()
    // Differences between LSP and legacy highlighting are collected and reported together at the end, so one run shows
    // all of them and still checks the edit and fallback steps.
    val problems = mutableListOf<String>()
    startIdeOnProject()
    step("Open the sample file", "The editor shows `lib/highlighting_sample.dart` with classes, enums, methods, parameters, and variables.")
    openProjectFile(SAMPLE_PATH)

    // 1. Legacy highlighting: the reference.
    inFrame {
      step("Turn off experimental LSP features", "Settings > Languages & Frameworks > Dart: \"Turn on experimental LSP features\" is unchecked and applied; the file is highlighted the legacy way.")
      setDartExperimentalLspFeatures(enabled = false)
    }
    check(!driver.dartExperimentalLspFeaturesEnabled()) { "\"Turn on experimental LSP features\" is still on" }
    waitForHighlightingMode(lsp = false)
    val legacy = inEditor { waitForStableHighlights(SAMPLE_EXPECTATIONS, "legacy highlighting") }
    println("Legacy highlighting: ${legacy.size} highlighted ranges")

    // 2. LSP highlighting: must match legacy.
    inFrame {
      step("Turn on experimental LSP features", "\"Turn on experimental LSP features\" is checked and applied; the file is highlighted from LSP semantic tokens and looks the same as before.")
      setDartExperimentalLspFeatures(enabled = true)
    }
    check(driver.dartExperimentalLspFeaturesEnabled()) { "\"Turn on experimental LSP features\" is still off" }
    waitForHighlightingMode(lsp = true)
    val lsp = inEditor { waitForStableHighlights(SAMPLE_EXPECTATIONS, "LSP highlighting") }
    println("LSP highlighting: ${lsp.size} highlighted ranges")
    val comparison = listOfNotNull(duplicatesIn(lsp, "LSP highlighting"), differences(reference = legacy, actual = lsp))
    if (comparison.isEmpty()) println("LSP highlighting matches legacy highlighting (${lsp.size} ranges), with nothing highlighted twice")
    problems += comparison

    // 3. Edits are highlighted from LSP semantic tokens too.
    inEditor {
      step("Add a function", "`int triple(int y) => y * 3;` is added at the end of the file; `triple` gets the function declaration color and `y` the parameter colors.")
      text = text.trimEnd() + "\n\n" + ADDED_FUNCTION
      moveCaretToText("triple")
      scrollToCaret()
      waitForStableHighlights(SAMPLE_EXPECTATIONS + EDIT_EXPECTATIONS, "LSP highlighting after an edit")
    }

    // 4. Fallback to legacy highlighting.
    inFrame {
      step("Turn off experimental LSP features again", "With the flag unchecked again, the file falls back to legacy highlighting with the same colors, including the new function.")
      setDartExperimentalLspFeatures(enabled = false)
    }
    waitForHighlightingMode(lsp = false)
    inEditor { waitForStableHighlights(SAMPLE_EXPECTATIONS + EDIT_EXPECTATIONS, "legacy highlighting after turning LSP off") }

    checkIdeLogForDartErrors()
    if (problems.isNotEmpty()) throw AssertionError(problems.joinToString("\n\n"))
  }

  /** Runs [action] on the sample file's editor. */
  private fun <T> inEditor(action: JEditorUiComponent.() -> T): T {
    var result: T? = null
    inFrame { result = editorFor("highlighting_sample.dart").action() }
    @Suppress("UNCHECKED_CAST")
    return result as T
  }

  /**
   * Waits until the Dart plugin reports LSP highlighting as active ([lsp] = true) or inactive. Fails with a clear
   * message if the Dart plugin under test doesn't have #640 at all.
   */
  private fun waitForHighlightingMode(lsp: Boolean) {
    val dart = driver.utility(DartAnalysisServerServiceStatics::class)
    val project = driver.singleProject()
    try {
      dart.isLspHighlightingActive(project)
    }
    catch (e: Exception) {
      throw AssertionError("The Dart plugin under test has no LSP highlighting (dart-intellij-third-party#640). Set DART_PLUGIN_ZIP to a Dart plugin built with it.", e)
    }
    if (lsp) {
      check(dart.isLspHighlightingEnabled(project)) {
        "LSP highlighting is not enabled although experimental LSP features are on; is the project's Dart SDK older than $MIN_SDK?"
      }
    }
    waitUntil("LSP highlighting to be ${if (lsp) "active" else "inactive"}", timeout = 2.minutes) {
      dart.isLspHighlightingActive(project) == lsp
    }
    println("LSP highlighting active: $lsp")
  }

  /**
   * Waits until every expectation holds and the highlighting has stopped changing (two identical snapshots in a row),
   * then returns it. Fails with the expectations that don't hold.
   */
  private fun JEditorUiComponent.waitForStableHighlights(expectations: List<Expect>, what: String): List<AttributesHighlight> {
    var previous: List<AttributesHighlight>? = null
    var current = emptyList<AttributesHighlight>()
    var failures = emptyList<String>()
    try {
      waitUntil("$what to show the expected colors", timeout = 3.minutes) {
        current = attributesHighlights(KEY_PREFIX).filter { it.key !in DIAGNOSTIC_KEYS }
        failures = expectations.mapNotNull { it.check(text, current) }
        val stable = failures.isEmpty() && current == previous
        previous = current
        if (failures.isEmpty() && !stable) Thread.sleep(2_000)
        stable
      }
    }
    catch (e: Exception) {
      throw AssertionError("$what: ${failures.size} of ${expectations.size} identifiers don't have the expected color:\n  " +
                           failures.joinToString("\n  "), e)
    }
    println("$what: all ${expectations.size} expected colors present")
    return current
  }

  /**
   * Ranges highlighted more than once, as they would be if two mechanisms highlighted the same thing, described; or
   * null if there are none.
   */
  private fun duplicatesIn(highlights: List<AttributesHighlight>, what: String): String? {
    val duplicates = highlights.groupBy { it.start to it.end }.filterValues { it.size > 1 }
    if (duplicates.isEmpty()) return null
    return "$what highlights ${duplicates.size} ranges more than once:\n  " +
           duplicates.entries.joinToString("\n  ") { (range, hs) -> "`${SAMPLE.substringSafe(range.first, range.second)}` at ${range.first}: ${hs.map { it.key }}" }
  }

  /**
   * Every range that LSP highlighting colors differently from legacy highlighting (counting each highlighter once),
   * described; or null if they match.
   */
  private fun differences(reference: List<AttributesHighlight>, actual: List<AttributesHighlight>): String? {
    val missing = reference.distinct() - actual.toSet()
    val extra = actual.distinct() - reference.toSet()
    if (missing.isEmpty() && extra.isEmpty()) return null
    fun List<AttributesHighlight>.describe() =
      if (isEmpty()) "(none)" else joinToString("\n    ") { "`${SAMPLE.substringSafe(it.start, it.end)}` at ${it.start}: ${it.key}" }
    return "LSP highlighting differs from legacy highlighting in ${missing.size + extra.size} places.\n" +
           "  Legacy only (${missing.size}):\n    ${missing.describe()}\n  LSP only (${extra.size}):\n    ${extra.describe()}"
  }

  /** Fails early if `FLUTTER_SDK`'s Dart SDK is too old for LSP highlighting. */
  private fun checkDartSdkSupportsLspHighlighting() {
    val versionFile = Platform.flutterSdk.resolve("bin/cache/dart-sdk/version")
    check(versionFile.exists()) { "No Dart SDK in $versionFile; run `flutter --version` with FLUTTER_SDK=${Platform.flutterSdk} once" }
    val version = versionFile.readText().trim()
    println("Dart SDK: $version")
    check(compareDartVersions(version, MIN_SDK) >= 0) {
      "LSP highlighting needs Dart SDK $MIN_SDK or newer; FLUTTER_SDK=${Platform.flutterSdk} has $version (for example use the Flutter 3.50 beta)"
    }
  }

  /** Fails if this run's IDE log has errors from the Dart plugin. */
  private fun checkIdeLogForDartErrors() {
    val entries = ideLogFile.readText().split(Regex("\n(?=\\d{4}-\\d{2}-\\d{2} )"))
    val errors = entries.filter { Regex("^\\S+ \\S+ \\[[^]]*]\\s+(SEVERE|ERROR)").containsMatchIn(it) && it.contains("com.jetbrains.lang.dart") }
    check(errors.isEmpty()) {
      "The IDE log has ${errors.size} errors from the Dart plugin; first:\n" + errors.first().lines().take(15).joinToString("\n")
    }
    println("No Dart plugin errors in $ideLogFile")
  }

  /** An identifier in the sample, found as [token] inside the unique text [context], and the color key it must have. */
  private data class Expect(val context: String, val token: String, val key: String) {
    /** Null if the highlights color this identifier with [key], otherwise a description of what's wrong. */
    fun check(text: String, highlights: List<AttributesHighlight>): String? {
      val at = text.indexOf(context)
      if (at < 0) return "`$context` not found in the file"
      val start = at + context.indexOf(token)
      val end = start + token.length
      val keys = highlights.filter { it.start == start && it.end == end }.map { it.key }
      return if (key in keys) null else "`$token` in `$context`: expected $key, got ${keys.ifEmpty { listOf("nothing") }}"
    }
  }

  private companion object {
    const val SAMPLE_PATH = "lib/highlighting_sample.dart"
    const val KEY_PREFIX = "DART_"

    /**
     * Error, warning, and hint underlines (for example the `avoid_print` lint on `print`) use `DART_` keys too, but they
     * are diagnostics, not syntax highlighting, and come from a different mechanism in LSP mode; they're not compared.
     */
    val DIAGNOSTIC_KEYS = setOf("DART_ERROR", "DART_WARNING", "DART_HINT")
    const val MIN_SDK = "3.14.0-307.0.dev"

    const val SAMPLE = """import 'dart:math' as math;

enum Color { red, green }

class Counter<T> {
  static int instances = 0;
  int value = 0;

  Counter(this.value);

  int get doubled => value * 2;

  static void reset() => instances = 0;

  void increment(int by) {
    value += by;
  }
}

int square(int x) => x * x;

void main() {
  final counter = Counter<String>(1);
  counter.increment(2);
  Counter.reset();
  print(counter.doubled + square(3) + math.max(1, 2));
  print(Color.red);
  outer:
  for (var i = 0; i < 2; i++) {
    if (i == 1) break outer;
  }
  print('tab:\t');
}
"""

    const val ADDED_FUNCTION = "int triple(int y) => y * 3;\n"

    /** One identifier of each kind the highlighting has to tell apart. */
    val SAMPLE_EXPECTATIONS = listOf(
      Expect("class Counter<T>", "Counter", "DART_CLASS"),
      Expect("class Counter<T>", "T", "DART_TYPE_PARAMETER"),
      Expect("enum Color", "Color", "DART_ENUM"),
      Expect("{ red, green }", "red", "DART_ENUM_CONSTANT"),
      Expect("int value = 0;", "value", "DART_INSTANCE_FIELD_DECLARATION"),
      Expect("int get doubled", "doubled", "DART_INSTANCE_GETTER_DECLARATION"),
      Expect("static void reset()", "reset", "DART_STATIC_METHOD_DECLARATION"),
      Expect("void increment(int by)", "increment", "DART_INSTANCE_METHOD_DECLARATION"),
      Expect("void increment(int by)", "by", "DART_PARAMETER_DECLARATION"),
      Expect("value += by", "by", "DART_PARAMETER_REFERENCE"),
      Expect("int square(int x)", "square", "DART_TOP_LEVEL_FUNCTION_DECLARATION"),
      Expect("final counter =", "counter", "DART_LOCAL_VARIABLE_DECLARATION"),
      Expect("counter.increment(2)", "counter", "DART_LOCAL_VARIABLE_REFERENCE"),
      Expect("counter.increment(2)", "increment", "DART_INSTANCE_METHOD_REFERENCE"),
      Expect("Counter.reset()", "reset", "DART_STATIC_METHOD_REFERENCE"),
      Expect("counter.doubled +", "doubled", "DART_INSTANCE_GETTER_REFERENCE"),
      Expect("square(3)", "square", "DART_TOP_LEVEL_FUNCTION_REFERENCE"),
      Expect("as math;", "math", "DART_IMPORT_PREFIX"),
      Expect("outer:", "outer", "DART_LABEL"),
      Expect("'tab:\\t'", "\\t", "DART_VALID_STRING_ESCAPE"),
    )

    val EDIT_EXPECTATIONS = listOf(
      Expect("int triple(int y)", "triple", "DART_TOP_LEVEL_FUNCTION_DECLARATION"),
      Expect("int triple(int y)", "y", "DART_PARAMETER_DECLARATION"),
      Expect("=> y * 3", "y", "DART_PARAMETER_REFERENCE"),
    )

    /** Compares Dart SDK versions like `3.14.0-315.3.beta` and `3.14.0-307.0.dev` (a release beats its prereleases). */
    fun compareDartVersions(a: String, b: String): Int {
      fun parse(v: String): Pair<List<Int>, List<Int>?> {
        val main = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val pre = v.substringAfter('-', "").takeIf { it.isNotEmpty() }?.split('.')?.mapNotNull { it.toIntOrNull() }
        return main to pre
      }
      val (am, ap) = parse(a)
      val (bm, bp) = parse(b)
      for (i in 0 until maxOf(am.size, bm.size)) {
        val c = am.getOrElse(i) { 0 }.compareTo(bm.getOrElse(i) { 0 })
        if (c != 0) return c
      }
      if (ap == null || bp == null) return (if (ap == null) 1 else 0) - (if (bp == null) 1 else 0)
      for (i in 0 until maxOf(ap.size, bp.size)) {
        val c = ap.getOrElse(i) { 0 }.compareTo(bp.getOrElse(i) { 0 })
        if (c != 0) return c
      }
      return 0
    }

    fun String.substringSafe(start: Int, end: Int) = if (start in 0..end && end <= length) substring(start, end) else "?"
  }
}
