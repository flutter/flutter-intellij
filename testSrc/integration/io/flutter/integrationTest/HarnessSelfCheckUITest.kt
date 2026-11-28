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
 * The harness's own smoke test, and the best first UI test to read.
 *
 * It checks that the UI test harness works end to end: a real IntelliJ IDEA starts on a project made by
 * `flutter create`, the Flutter and Dart plugins load, the Flutter plugin registers its actions, a Dart file opens in
 * the editor, and the Flutter device selector appears on the toolbar. If this test fails, no other UI test result
 * means anything yet: fix the environment or the harness first.
 *
 * ## Running it
 *
 * From the repository root, with `FLUTTER_SDK` pointing at a Flutter SDK and an unlocked desktop (on Windows and macOS
 * the IDE opens on your screen; don't click in it while the test runs):
 *
 * ```
 * ./gradlew integration --tests io.flutter.integrationTest.HarnessSelfCheckUITest      # Linux, macOS
 * .\gradlew.bat integration --tests io.flutter.integrationTest.HarnessSelfCheckUITest  # Windows
 * ```
 *
 * No other options are needed. A run takes about 1.5 minutes; the first one also downloads the IDE (about 1 GB) into
 * `build/out/ide-tests/`.
 *
 * ## Two processes
 *
 * The most important thing to understand about every UI test here: **this code does not run inside the IDE.**
 *
 * - The *test JVM*, started by Gradle, runs this class. It is ordinary JUnit 5 code.
 * - The *IDE*, started by JetBrains' IDE Starter framework, is a separate process: a real IntelliJ IDEA with our plugin
 *   installed, in a throwaway sandbox under `build/out/ide-tests/tests/<IDE build>/HarnessSelfCheckUITest/`.
 * - The two talk over JetBrains' *Driver* protocol. `driver` (from [FlutterIdeUITest]) is the test's handle to the IDE.
 *   It does two kinds of things: call IDE code (`driver.service(ActionManager::class).getAction(...)` runs `getAction`
 *   inside the IDE and returns the answer), and find and operate Swing components through XPath-like queries
 *   (`x("//div[...]")`, `xx(...)`), where every component is a `div` with attributes such as `class`, `javaclass`,
 *   `text`, `accessiblename` and `myicon`.
 *
 * So a check like "the editor contains `MaterialApp`" really means "ask the IDE for the text of the editor component
 * and compare it here".
 *
 * ## What happens when the test runs, in order
 *
 * 1. **Before the test (JUnit `@BeforeAll`, in [ExistingProjectUITest.createProject]).** `flutter create` makes a fresh
 *    project named after the constructor argument, `harness_check`, in a folder `harness_check_<timestamp>` (under
 *    `%USERPROFILE%\ft` on Windows to keep paths short, the system temp directory elsewhere).
 * 2. **[startIdeOnProject]** builds the IDE configuration ([Setup]: which IDE build, which plugins, which settings),
 *    then launches the IDE on that project and connects Driver. Along the way it:
 *    - picks the Dart plugin and checks that this IDE build can load it, before anything starts (`DartPluginSelection`);
 *    - runs the **plugin-loaded gate**: `io.flutter` and `Dart` must be loaded and enabled, otherwise the test fails
 *      immediately, quoting the IDE's own reason from its log;
 *    - waits for the project window and for background work (indexing) to finish.
 * 3. **The test body below**: one step and four oracles (see "Steps and oracles").
 * 4. **After the test (JUnit `@AfterEach`, [FlutterIdeUITest.tearDownIde]).** Closes the IDE, then two more gates,
 *    each of which can fail a test whose body passed:
 *    - the **`idea.log` gate**: any `SEVERE`/`ERROR` entry in the IDE's log that mentions `io.flutter` or the Dart
 *      plugin (minus a short, justified allowlist in `IdeLog`) fails the test. This catches errors the UI never shows;
 *    - the **leaked-process check**: `flutter`/`dart` processes the test started and left running are killed and fail
 *      the test.
 * 5. **Results are written** to `build/agent-testing/run-result.json` (see "Reading the results").
 * 6. **After all tests in the class (`@AfterAll`)**: the project folder is deleted, unless `KEEP_TEST_PROJECT=true`.
 *
 * ## Steps and oracles
 *
 * The body uses two harness functions instead of bare code, and the difference matters:
 *
 * - `step("...") { }` is something a user does (here: open a file). Its title is written the way a person would
 *   describe the action, because it becomes a caption in recorded walkthroughs and a step name in the results.
 * - `assertOracle("O1", "...") { }` is a check: what a user would see if the plugin works. All of a check's assertions
 *   go inside its block. Oracles are numbered `O1`, `O2`, ... and their descriptions say what is expected.
 *
 * Both record their outcome and duration, so a failure report says exactly where things went wrong, for example
 * "failed at O3 after every step completed" rather than a bare stack trace. That distinction is what lets the
 * agent-testing tooling tell a real product failure (an oracle failed) from a broken test (a step failed). Feature
 * tests normally follow the pattern *pre-state oracle, steps, oracle*; this test does the same with `O1` as its
 * pre-state.
 *
 * ## Reading the results
 *
 * - **Console / test report** (`build/test-results/integration/TEST-...HarnessSelfCheckUITest.xml`): the test's output,
 *   including the Dart plugin it chose and why, and `[timing]` lines for every harness phase (project creation, IDE
 *   launch, plugin gate, indexing, shutdown) plus a summary at the end. When a run is slow, start there.
 * - **`build/agent-testing/run-result.json`**: a machine-readable summary: IDE build, plugin versions, every step and
 *   oracle with status and duration, phase timings, and the first failure with its phase (`PREFLIGHT`, `SETUP`, `STEP`,
 *   `ORACLE`, `TEARDOWN`).
 * - **The IDE's own files**, under `build/out/ide-tests/tests/<IDE build>/HarnessSelfCheckUITest/log/`: `idea.log`
 *   (the IDE's log; plugin loading problems are near the top under "Problems found loading plugins"), `screenshots/`
 *   (Starter saves one whenever a Driver call fails), and `monitoring-thread-dumps-ide/` (thread dumps, for freezes).
 *
 * ## The plugin-off control
 *
 * A UI test that would still pass without our plugin proves nothing. So every UI test must also *fail* when the
 * Flutter plugin isn't installed, and this one is the first place that is checked: with
 * `FLUTTER_UI_TEST_WITHOUT_PLUGIN=true` the harness installs no Flutter plugin and skips the plugin-loaded gate, and
 * this test must then fail at `O2`, because none of the Flutter actions exist.
 *
 * ## Writing your own test from this one
 *
 * Copy the shape: extend [ExistingProjectUITest] with a package name, call [startIdeOnProject], then alternate `step`
 * and `assertOracle` blocks. Feature tests are written from a workflow spec in `testSrc/integration/specs/`; this test
 * is not a workflow spec test and has no entry in the spec catalog. For the full manual (Driver queries, the helpers,
 * platform differences, pitfalls already hit) see `.agents/skills/ui-test-reference/SKILL.md`; for how tests are
 * certified, `.agents/design-docs/agent-integration-testing.md`.
 */
// `harness` marks this as the harness's own test rather than a feature test. FlutterIdeUITest adds the `ui` tag that
// every UI test carries. Runners can include or exclude tests by tag.
@Tag("harness")
// The constructor argument is the package name passed to `flutter create`: it becomes the project's name, the module
// shown at the top of the Project view, and the prefix of the project folder.
class HarnessSelfCheckUITest : ExistingProjectUITest("harness_check") {

  @Test
  fun pluginLoadsAndWiresItsUi() {
    // Launch the IDE on the project that @BeforeAll created and wait until it is open and indexed. Everything up to
    // here counts as the SETUP phase: a failure in this call (IDE didn't start, plugins didn't load, indexing never
    // finished) is an environment or harness problem, not a failed oracle.
    startIdeOnProject()

    // O1, the pre-state: before doing anything, confirm the IDE opened the right project. `inFrame { }` runs the
    // block against the project window's component tree, `projectView { }` finds the Project tool window inside it,
    // and `pathExists` walks its tree: the project's root node, then `pubspec.yaml` under it. `waitFound()` first
    // waits for the tree component to exist, because the tool window can still be building when indexing finishes.
    assertOracle("O1", "the project opened: the Project view lists pubspec.yaml and indexing finished") {
      inFrame { projectView { projectViewTree.waitFound().pathExists("harness_check", "pubspec.yaml") } }
    }

    // The one user action: open lib/main.dart, as double-clicking it in the Project view would. `openProjectFile` asks
    // the IDE to open the file by its path relative to the project root, waits for the editor and for code analysis to
    // finish, and closes any pop-up the IDE shows on its own (a trial survey, for example) before and after.
    step("Open lib/main.dart from the Project view") {
      openProjectFile("lib/main.dart")
    }

    // O2: the Flutter plugin registered its actions with the IDE. This calls IDE code directly rather than looking at
    // the UI: `driver.service(ActionManager::class)` is a proxy for the IDE's ActionManager service, so each
    // `getAction(id)` runs inside the IDE and returns null for an unknown ID. The three IDs come from the Flutter
    // plugin's plugin.xml: the Hot Reload and Hot Restart toolbar buttons, and Flutter > Pub Get. If the plugin isn't
    // loaded, none of them exist; this is the oracle the plugin-off control must fail.
    assertOracle("O2", "the Flutter plugin registered its actions (hot reload, pub get) with the IDE") {
      val actions = driver.service(ActionManager::class)
      for (id in listOf("Flutter.Toolbar.ReloadAction", "Flutter.Toolbar.RestartAction", "flutter.pub.get")) {
        assertNotNull(actions.getAction(id), "action '$id' is not registered; is the Flutter plugin loaded?")
      }
    }

    // O3: what a user sees after the step. Two checks, both through the UI:
    // - the editor tab for main.dart shows the `flutter create` template, which always contains `MaterialApp`;
    //   `editorFor` finds the editor component for that file and `.text` reads its contents from the IDE;
    // - the Flutter device selector is on the main toolbar. It is found by its Java class (`DeviceSelectorAction`,
    //   from the Flutter plugin) because it has no stable visible text: it reads "Loading Devices...", "No Devices" or
    //   a device name depending on what's connected. It only appears once the plugin has recognised the project as a
    //   Flutter project, which can take a moment, so `waitUntil` polls every 2 seconds for up to 3 minutes, closing any
    //   IDE pop-ups between polls, instead of checking once. `xx(...)` returns every match (possibly none); `x(...)`
    //   would demand exactly one.
    assertOracle("O3", "main.dart is open in the editor and the Flutter device selector is on the toolbar") {
      inFrame {
        assertTrue(editorFor("main.dart").text.contains("MaterialApp"), "main.dart editor does not show the Flutter template")
        waitUntil("device selector to appear on the toolbar") {
          xx("//div[contains(@javaclass, 'DeviceSelectorAction')]").list().isNotEmpty()
        }
      }
    }

    // O4: nothing has gone visibly wrong. `consoleText()` reads every console view in the window (Run, Debug,
    // Messages, Flutter output) and only those, so text in a source file can't trigger it. No app was run, so the
    // consoles should be empty or show only routine output such as `pub get`. The deeper error checks (the idea.log
    // gate and the leaked-process check) run after the IDE closes, in teardown, and can still fail this test.
    assertOracle("O4", "no console errors; idea.log and process checks run in teardown") {
      inFrame {
        val console = consoleText()
        assertTrue(!console.contains("Exception") && !console.contains("Error:"), "a console shows an error: $console")
      }
    }
  }
}
