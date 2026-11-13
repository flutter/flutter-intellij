/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.codeEditorForFile
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.project.LocalProjectInfo
import com.intellij.ide.starter.project.NoProject
import com.intellij.driver.sdk.ui.components.common.FileChooserDialogUi
import com.intellij.driver.sdk.ui.components.common.welcomeScreen
import io.flutter.integrationTest.utils.Platform
import io.flutter.integrationTest.utils.RunResult.Phase
import io.flutter.integrationTest.utils.createFlutterProjectWithCli
import io.flutter.integrationTest.utils.deleteFlutterProject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Base class for UI tests that start with the IDE already open on an existing Flutter project.
 *
 * The project is created with `flutter create` (not the plugin's New Project wizard) in a temp directory before the
 * IDE starts, and deleted afterwards unless `KEEP_TEST_PROJECT=true`. Set `KEEP_IDE_OPEN_MINUTES` to leave the IDE
 * running after the test. Requires the `FLUTTER_SDK` environment variable.
 *
 * The plugin-loaded gate, `step`/`assertOracle`/`probe`, the `idea.log` gate, and process cleanup come from
 * [FlutterIdeUITest].
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ExistingProjectUITest(private val packageName: String) : FlutterIdeUITest() {

  private val projectFolder = "${packageName}_${System.currentTimeMillis()}"
  private val parentDir: String = Platform.testProjectsDir.toString()

  /** Root of the test project on disk. */
  protected val projectDir: Path = Paths.get(parentDir, projectFolder)

  @BeforeAll
  fun createProject() {
    createFlutterProjectWithCli(projectFolder, projectName = packageName, directory = parentDir)
    prepareProject()
  }

  /** Hook for subclasses to adjust the project on disk before the IDE opens it, for example to apply an overlay. */
  protected open fun prepareProject() {}

  @AfterAll
  fun deleteProject() {
    if (System.getenv("KEEP_TEST_PROJECT").toBoolean()) {
      println("Keeping test project: $projectDir")
    }
    else {
      deleteFlutterProject(projectFolder, directory = parentDir)
    }
  }

  /**
   * Starts the IDE on [projectDir] and, by default, waits until the project is open and indexed. Pass
   * `waitForReady = false` when the project opening itself is what the test checks (then call [waitForProjectReady]
   * inside the oracle, so "no project window appeared" is an oracle failure, not a setup failure).
   */
  protected fun startIdeOnProject(waitForReady: Boolean = true) {
    println("Opening existing project $projectDir")
    step(
      "Open the existing Flutter project",
      "IntelliJ IDEA starts (splash screen), then opens the project created with `flutter create`. " +
      "The Project view shows its files and the status bar shows indexing until it finishes."
    )
    startIde(Setup.setupTestContextIU262(javaClass.simpleName, LocalProjectInfo(projectDir))) { it.runIdeWithDriver() }
    result.currentPhase = Phase.SETUP
    if (waitForReady) waitForProjectReady()
  }

  /**
   * Waits for the project frame to appear and background indicators (indexing) to finish, retrying a few times while
   * the frame settles, then closes the Trial tab if it opened.
   */
  protected fun waitForProjectReady(attempts: Int = 3, frameTimeout: Duration = 2.minutes) {
    repeat(attempts) { attempt ->
      try {
        inFrame {
          waitFound(frameTimeout)
          driver.waitForIndicators(5.minutes)
        }
        println("Project is open and indexed.")
        // The Trial tab usually opens while the project loads, but can arrive shortly after; poll briefly here and
        // again when opening files or polling conditions in [dismissInterruptions].
        val deadline = System.currentTimeMillis() + 3_000
        while (!closeTrialTab() && System.currentTimeMillis() < deadline) Thread.sleep(500)
        return
      }
      catch (e: Exception) {
        if (attempt == attempts - 1) throw e
        println("Project frame not stable yet (${e.javaClass.simpleName}); retrying")
        Thread.sleep(5_000)
      }
    }
  }

  /** True while the IDE shows its welcome screen (no project frame). */
  protected fun welcomeScreenShowing(): Boolean = runCatching {
    driver.ui.xx("//div[@class='FlatWelcomeFrame']").list().isNotEmpty()
  }.getOrDefault(false)

  /**
   * Starts the IDE with **no** project, on the welcome screen. Use with [openProjectFromWelcomeScreen] when the test is
   * about opening the project the way a user does (*File > Open*), rather than about what happens once it's open.
   */
  protected fun startIdeOnWelcomeScreen() {
    println("Starting the IDE on the welcome screen (project to open later: $projectDir)")
    startIde(Setup.setupTestContextIU262(javaClass.simpleName, NoProject)) { it.runIdeWithDriver() }
    result.currentPhase = Phase.SETUP
    driver.ui.welcomeScreen { waitFound(2.minutes) }
    println("Welcome screen is showing")
  }

  /**
   * Clicks *Open* on the welcome screen, types [projectDir] into the file chooser, and confirms: the user's
   * *File > Open* path, which goes through the IDE's `ProjectOpenProcessor` chain. Returns once the dialog has closed;
   * it does not wait for a project window, so a test can make "a window appears" its oracle.
   */
  protected fun openProjectFromWelcomeScreen() {
    driver.ui.welcomeScreen { openProjectButton.click() }
    val chooser = driver.ui.x("//div[@class='MyDialog' and contains(@title, 'Open')]", FileChooserDialogUi::class.java)
    chooser.waitFound(30.seconds)
    chooser.selectFolder(projectDir)
    chooser.okButton.click()
    waitFor("the Open dialog to close", 30.seconds, 500.milliseconds) { chooser.notPresent() }
  }

  /**
   * Selects [dir] in the file chooser by walking its folder tree row by row: find the row for each path segment
   * (below the previous one), expand it, wait for the next segment's row to appear (children load lazily), and
   * select the last one. This mirrors clicking the expanders. The 2026.2 chooser has no path text field (typing goes to
   * the tree's speed search), and Driver's path-based helpers only see nodes that are already expanded, so rows are
   * the dependable handle. Fails with a hierarchy dump if a segment never appears.
   */
  protected fun FileChooserDialogUi.selectFolder(dir: Path) {
    val tree = fileTree
    tree.waitFound(30.seconds)
    // The fixture handle goes stale when the tree model changes (DriverIllegalStateException on valueAt), so it is
    // re-fetched for every call.
    // Resolve symlinks so the segments match what the tree shows (macOS: /var -> /private/var), and take the root
    // from the path instead of assuming "/": on Windows it is a drive, "C:\".
    val target = dir.toRealPath()
    val rootLabel = target.root?.toString()?.let { if (it == "/") it else it.removeSuffix("\\") }
                    ?: error("$dir has no root component")
    val segments = listOf(rootLabel) + target.map { it.toString() }
    println("Open dialog: navigating ${segments.joinToString(" > ")}")

    fun rowOf(text: String, after: Int, isRoot: Boolean): Int? {
      var row = after + 1
      while (row < 5_000) {
        val value = runCatching { tree.fixture.valueAt(row) }.getOrNull() ?: return null
        // The root row's label depends on the platform ("/", "C:\", "Local Disk (C:)"): match it loosely. Every other
        // segment must match exactly so a sibling with a longer name is never picked.
        if (value == text || (isRoot && value.contains(text))) return row
        row++
      }
      return null
    }

    var previous = -1
    for ((i, segment) in segments.withIndex()) {
      var row = -1
      try {
        waitFor("'$segment' to appear in the folder tree", 30.seconds, 500.milliseconds) {
          row = rowOf(segment, previous, isRoot = i == 0) ?: -1
          row >= 0
        }
      }
      catch (e: Exception) {
        probe("open-dialog-missing-$segment")
        val below = (previous + 1 until previous + 40).map { r -> runCatching { tree.fixture.valueAt(r) }.getOrElse { "<${it.javaClass.simpleName}: ${it.message?.take(60)}>" } }
        throw AssertionError("The Open dialog's folder tree never showed '$segment' below row $previous; rows there: $below", e)
      }
      if (i < segments.size - 1) {
        // Expand only if collapsed: the dialog opens with the home directory's ancestors already expanded, and
        // toggling one of those shut would hide the rest of the path.
        val nextValue = runCatching { tree.fixture.valueAt(row + 1) }.getOrNull()
        val alreadyExpanded = nextValue != null && segments.getOrNull(i + 1) == nextValue
        if (!alreadyExpanded) tree.fixture.expandRow(row)
      }
      else {
        tree.fixture.selectRow(row)
      }
      previous = row
    }
    waitFor("the Open dialog to select ${dir.fileName}", 10.seconds, 500.milliseconds) {
      tree.collectSelectedPaths().any { it.path.lastOrNull() == dir.fileName.toString() }
    }
    println("Open dialog: selected ${dir.fileName}")
  }

  /** Runs [action] inside the project frame. */
  protected fun inFrame(action: IdeaFrameUI.() -> Unit) {
    driver.withContext { ideFrame { action() } }
  }

  /** Opens [relativePath] (relative to the project root) in an editor and waits for code analysis. */
  protected fun openProjectFile(relativePath: String) {
    dismissInterruptions()
    driver.openFile(relativePath)
    dismissInterruptions()
  }

  /** The editor for [fileName], e.g. `main.dart`. */
  protected fun IdeaFrameUI.editorFor(fileName: String): JEditorUiComponent = codeEditorForFile(fileName)

  // ---------------------------------------------------------------------------------------------------------------
  // Consoles

  /**
   * Text of the console views in the frame (Run, Debug, Messages, Flutter consoles), and only those: source editors are
   * excluded, so a string in `main.dart` or `pubspec.yaml` can't satisfy a console check. Pass [toolWindow] (the tool
   * window's title, for example `Run`) to read only consoles inside that tool window. The result starts at
   * [sinceOffset], so a test can record the length before its trigger step and read only what came after.
   */
  protected fun IdeaFrameUI.consoleText(toolWindow: String? = null, sinceOffset: Int = 0): String {
    val scope = if (toolWindow == null) "" else "//div[@class='InternalDecoratorImpl'][.//div[@accessiblename='$toolWindow' or @text='$toolWindow']]"
    // `@class` is the simple class name and matches exactly; the Flutter run console is a `DaemonConsoleView`
    // (a `ConsoleViewImpl` subclass), so match on the fully qualified `@javaclass` instead.
    val all = xx("$scope//div[contains(@javaclass, 'ConsoleView')]//div[@class='EditorComponentImpl']", JEditorUiComponent::class.java).list()
      .joinToString("\n") { runCatching { it.text }.getOrDefault("") }
    return when {
      sinceOffset <= 0 -> all
      sinceOffset >= all.length -> ""
      else -> all.substring(sinceOffset)
    }
  }

  /** Length of [consoleText] now, to pass as `sinceOffset` after the trigger step. */
  protected fun IdeaFrameUI.consoleOffset(toolWindow: String? = null): Int = consoleText(toolWindow).length

  /**
   * Waits until a console contains one of [texts]; returns the one found. Fails fast if one of [failTexts] appears
   * first, so an error doesn't turn into a timeout. Reads consoles only, never source editors.
   */
  protected fun IdeaFrameUI.waitForConsoleText(
    vararg texts: String,
    toolWindow: String? = null,
    sinceOffset: Int = 0,
    failTexts: List<String> = emptyList(),
    timeout: Duration = 3.minutes,
  ): String {
    var found = ""
    waitFor("console to contain one of ${texts.toList()}", timeout, 2.seconds) {
      dismissInterruptions()
      val all = consoleText(toolWindow, sinceOffset)
      failTexts.firstOrNull { all.contains(it) }?.let { bad ->
        throw AssertionError("Console shows '$bad' while waiting for one of ${texts.toList()}")
      }
      found = texts.firstOrNull { all.contains(it) } ?: ""
      found.isNotEmpty()
    }
    println("Found console text: $found")
    return found
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Devices and apps

  /** Opens the Flutter device selector and clicks the device whose name starts with one of [names]. */
  protected fun IdeaFrameUI.selectDevice(vararg names: String) {
    require(names.isNotEmpty()) { "At least one device name must be provided" }
    fun String.matches() = names.any { startsWith(it, ignoreCase = true) }
    val selector = x(
      "//div[contains(@javaclass, 'DeviceSelectorAction')]//div[@class='JBLabel' and @text!=''] | " +
      "//div[@class='JBLabel' and (contains(@text, '(desktop)') or contains(@text, '(web)') or " +
      "contains(@text, '(mobile)') or @text='No Devices' or @text='No Device Selected' or @text='Loading Devices...')]"
    )
    var attempt = 0
    waitUntil("device ${names.toList()} to be selected", timeout = 3.minutes) {
      val currentTexts = selector.getAllTexts().map { it.text }
      if (currentTexts.any { it.matches() }) return@waitUntil true
      if (currentTexts.isEmpty() || currentTexts.any { it == "Loading Devices..." }) return@waitUntil false
      val name = names[attempt++ % names.size]
      selector.click()
      runCatching {
        waitFor("device popup to open", 5.seconds, 200.milliseconds) {
          driver.ui.xx("//div[@class='MyList' or @class='JBList']").list().isNotEmpty()
        }
      }
      // Use the popup's speed search. If nothing matches (the device daemon may not have listed the device yet, or
      // the name differs), close the popup so the typed text can't land in the editor, and try the next name.
      keyboard { typeText(name, 50) }
      val nothingToShow = runCatching {
        waitFor("speed search result", 1.seconds, 200.milliseconds) {
          x("//div[contains(@visible_text, 'Nothing to show')]").present() ||
          driver.ui.xx("//div[contains(@visible_text, 'Nothing to show')]").list().isNotEmpty()
        }
        true
      }.getOrDefault(false)
      if (nothingToShow) {
        keyboard { escape() }
        false
      }
      else {
        keyboard { enter() }
        runCatching {
          waitFor("device '$name' to be selected", 5.seconds, 200.milliseconds) {
            selector.getAllTexts().any { it.text.matches() }
          }
          true
        }.getOrDefault(false)
      }
    }
    println("Selected device: ${selector.getAllTexts().joinToString("") { it.text }}")
  }

  /**
   * Stops the running app by clicking a Stop button (in the toolbar or the Run/Debug tool window), like a user would,
   * and **fails** unless the process ends: the Stop buttons go disabled and the console reports the process finished.
   * The Stop shortcut differs between keymaps (Ctrl+F2 vs. ⌘F2 on macOS), and invoking the `Stop` action with the
   * editor as context is rejected as disabled, so the button is the reliable path.
   */
  protected fun IdeaFrameUI.stopAppAndVerifyTerminated(timeout: Duration = 30.seconds) {
    val stop = xx("//div[@class='ActionButton' and @myicon='stop.svg']").list().firstOrNull { it.isEnabled() }
               ?: throw AssertionError("No enabled Stop button: is an app running?")
    stop.click()
    waitFor("app process to stop", timeout, 500.milliseconds) {
      xx("//div[@class='ActionButton' and @myicon='stop.svg']").list().none { it.isEnabled() }
    }
    waitFor("console to report the process finished", timeout, 500.milliseconds) {
      val text = consoleText()
      text.contains("Process finished") || text.contains("Application finished") || text.contains("Lost connection to device")
    }
    println("App stopped")
  }

  /** Best-effort stop for cleanup paths; prefer [stopAppAndVerifyTerminated] in test steps. */
  protected fun IdeaFrameUI.stopApp() {
    runCatching { stopAppAndVerifyTerminated() }
      .onFailure { println("Could not stop the app (${it.message}); it stops when the IDE closes.") }
  }

}
