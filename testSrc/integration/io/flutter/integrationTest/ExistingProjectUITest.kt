/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.openFile
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.driver.sdk.ui.components.common.codeEditorForFile
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.project.LocalProjectInfo
import io.flutter.integrationTest.utils.Platform
import io.flutter.integrationTest.utils.WalkthroughSteps
import io.flutter.integrationTest.utils.createFlutterProjectWithCli
import io.flutter.integrationTest.utils.deleteFlutterProject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Base class for UI tests that start with IntelliJ IDEA 2026.2.3 already open on an existing Flutter project.
 *
 * The project is created with `flutter create` (not the plugin's New Project wizard) in a temp directory before the
 * IDE starts, and deleted afterwards unless `KEEP_TEST_PROJECT=true`. Set `KEEP_IDE_OPEN_MINUTES` to leave the IDE
 * running after the test. Requires the `FLUTTER_SDK` environment variable.
 */
@Tag("ui")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ExistingProjectUITest(private val packageName: String) {

  private val projectFolder = "${packageName}_${System.currentTimeMillis()}"
  private val parentDir: String = Platform.testProjectsDir.toString()

  /** Root of the test project on disk. */
  protected val projectDir: Path = Paths.get(parentDir, projectFolder)

  protected lateinit var run: BackgroundRun

  /** The IDE Starter context of the running IDE (paths, VM options). */
  protected lateinit var context: IDETestContext

  /** This run's IDE log. IDE Starter starts each test with a fresh test home, so it only covers this run. */
  protected val ideLogFile: Path
    get() = context.paths.testHome.resolve("log").resolve("idea.log")

  protected val driver: Driver
    get() = run.driver

  @BeforeAll
  fun createProject() {
    createFlutterProjectWithCli(projectFolder, projectName = packageName, directory = parentDir)
    prepareProject()
  }

  /** The Dart plugin version to install. Override for tests of features that need a newer Dart plugin. */
  protected open val dartPluginVersion: String = Setup.DART_PLUGIN_VERSION_IU262

  /** Hook for subclasses to adjust the project on disk before the IDE opens it. */
  protected open fun prepareProject() {}

  @AfterEach
  fun closeIde() {
    WalkthroughSteps.finish()
    if (!::run.isInitialized) return
    val keepOpenMinutes = System.getenv("KEEP_IDE_OPEN_MINUTES")?.toLongOrNull() ?: 0L
    if (keepOpenMinutes > 0) {
      println("Keeping IDE open for $keepOpenMinutes minute(s)")
      Thread.sleep(keepOpenMinutes * 60_000)
    }
    run.closeIdeAndWait()
  }

  @AfterAll
  fun deleteProject() {
    if (System.getenv("KEEP_TEST_PROJECT").toBoolean()) {
      println("Keeping test project: $projectDir")
    }
    else {
      deleteFlutterProject(projectFolder, directory = parentDir)
    }
  }

  /** Marks the start of a user-visible step for walkthrough recordings; see [WalkthroughSteps]. */
  protected fun step(title: String, expect: String) = WalkthroughSteps.step(title, expect)

  /** Starts the IDE on [projectDir] and waits until the project is open and indexed. */
  protected fun startIdeOnProject() {
    println(Platform.describe())
    println("Opening existing project $projectDir")
    step(
      "Open the existing Flutter project",
      "IntelliJ IDEA starts (splash screen), then opens the project created with `flutter create`. " +
      "The Project view shows its files and the status bar shows indexing until it finishes."
    )
    context = Setup.setupTestContextIU262(javaClass.simpleName, LocalProjectInfo(projectDir), dartPluginVersion)
    run = context.runIdeWithDriver()
    waitForProjectReady()
    // The Trial tab usually opens while the project loads, but can come a few seconds later; give it a moment.
    val deadline = System.currentTimeMillis() + 15_000
    while (!closeTrialTab() && System.currentTimeMillis() < deadline) Thread.sleep(1_000)
  }

  /** Runs [action] inside the project frame. */
  protected fun inFrame(action: IdeaFrameUI.() -> Unit) {
    driver.withContext { ideFrame { action() } }
  }

  /** Opens [relativePath] (relative to the project root) in an editor and waits for code analysis. */
  protected fun openProjectFile(relativePath: String) {
    driver.openFile(relativePath)
  }

  /** The editor for [fileName], e.g. `main.dart`. */
  protected fun IdeaFrameUI.editorFor(fileName: String): JEditorUiComponent = codeEditorForFile(fileName)

  /** Text of all editors in the frame, including run and messages consoles. */
  protected fun IdeaFrameUI.allEditorText(): String =
    xx("//div[@class='EditorComponentImpl']", JEditorUiComponent::class.java).list()
      .joinToString("\n") { runCatching { it.text }.getOrDefault("") }

  /**
   * Closes dialogs the IDE may pop up on its own during a test (for example the trial feedback survey "Tell us about
   * your experience"), which would otherwise block the UI. Called while polling.
   */
  protected fun dismissInterruptions() {
    runCatching {
      driver.ui.xx("//div[@class='JButton' and (@visible_text='No, Thanks' or @visible_text='No, thanks')]").list()
        .firstOrNull()?.let {
          println("Dismissing an IDE interruption dialog")
          it.click()
        }
    }
    closeTrialTab()
  }

  /**
   * Closes the "Trial" editor tab that IntelliJ IDEA Ultimate opens on its own after it starts a free trial (logged as
   * "Automatically opened an editor tab for 'active' trial state"). It covers the editor and shows up in walkthroughs.
   * Returns true if a tab was closed.
   */
  protected fun closeTrialTab(): Boolean = runCatching {
    val close = driver.ui.xx("//div[@class='EditorTabLabel'][.//div[@visible_text='Trial']]//div[@myicon='closeSmall.svg']")
      .list().firstOrNull() ?: return false
    println("Closing IntelliJ IDEA's Trial tab")
    close.click()
    true
  }.getOrDefault(false)

  /** Waits until any editor or console in the frame contains one of [texts]; returns the one found. */
  protected fun IdeaFrameUI.waitForConsoleText(vararg texts: String, timeout: Duration = 3.minutes): String {
    var found = ""
    waitFor("console to contain one of ${texts.toList()}", timeout, 2.seconds) {
      dismissInterruptions()
      val all = allEditorText()
      found = texts.firstOrNull { all.contains(it) } ?: ""
      found.isNotEmpty()
    }
    println("Found console text: $found")
    return found
  }

  /** Waits until [condition] holds, polling every 2 seconds. */
  protected fun waitUntil(message: String, timeout: Duration = 3.minutes, condition: () -> Boolean) {
    waitFor(message, timeout, 2.seconds) {
      dismissInterruptions()
      condition()
    }
  }

  /** Opens the Flutter device selector and clicks the device whose name starts with one of [names]. */
  protected fun IdeaFrameUI.selectDevice(vararg names: String) {
    fun String.matches() = names.any { startsWith(it, ignoreCase = true) }
    val selector = x(
      "//div[@class='JBLabel' and (contains(@text, '(desktop)') or contains(@text, '(web)') or " +
      "contains(@text, '(mobile)') or contains(@text, 'device') or contains(@text, 'Device'))]"
    )
    var attempt = 0
    waitUntil("device ${names.toList()} to be selected", timeout = 3.minutes) {
      if (selector.getAllTexts().any { it.text.matches() }) return@waitUntil true
      val name = names[attempt++ % names.size]
      selector.click()
      Thread.sleep(1_000)
      // Use the popup's speed search. If nothing matches (the device daemon may not have listed the device yet, or
      // the name differs), close the popup so the typed text can't land in the editor, and try the next name.
      keyboard { typeText(name, 50) }
      Thread.sleep(1_000)
      if (x("//div[contains(@visible_text, 'Nothing to show')]").present() ||
          driver.ui.xx("//div[contains(@visible_text, 'Nothing to show')]").list().isNotEmpty()) {
        keyboard { escape() }
      }
      else {
        keyboard { enter() }
      }
      Thread.sleep(2_000)
      selector.getAllTexts().any { it.text.matches() }
    }
    println("Selected device: ${selector.getAllTexts().joinToString("") { it.text }}")
  }

  /**
   * Stops the running app by clicking a Stop button (in the toolbar or the Run/Debug tool window), like a user would.
   * The Stop shortcut differs between keymaps (Ctrl+F2 vs. ⌘F2 on macOS), and invoking the `Stop` action with the
   * editor as context is rejected as disabled.
   */
  protected fun IdeaFrameUI.stopApp() {
    runCatching {
      val stop = xx("//div[@class='ActionButton' and @myicon='stop.svg']").list().firstOrNull { it.isEnabled() }
                 ?: error("no enabled Stop button")
      stop.click()
      Thread.sleep(3_000)
      val stillRunning = xx("//div[@class='ActionButton' and @myicon='stop.svg']").list().any { it.isEnabled() }
      println(if (stillRunning) "Clicked Stop, but a Stop button is still enabled" else "App stopped")
    }.onFailure { println("Could not stop the app (${it.message}); it stops when the IDE closes.") }
  }

  private fun waitForProjectReady(attempts: Int = 3) {
    repeat(attempts) { attempt ->
      try {
        inFrame {
          waitFound(2.minutes)
          driver.waitForIndicators(5.minutes)
        }
        println("Project is open and indexed.")
        return
      }
      catch (e: Exception) {
        if (attempt == attempts - 1) throw e
        println("Project frame not stable yet (${e.javaClass.simpleName}); retrying")
        Thread.sleep(5_000)
      }
    }
  }
}
