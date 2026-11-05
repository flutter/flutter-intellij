/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import io.flutter.integrationTest.utils.AndroidEmulator
import io.flutter.integrationTest.utils.VmServiceClient
import io.flutter.integrationTest.utils.vmServiceUriFromConsole
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

/**
 * **Shows:** running a Flutter app from the IDE on a device and applying a code change with hot reload.
 *
 * Steps:
 * 1. IntelliJ IDEA opens an existing Flutter app (created with `flutter create`). For the Android emulator variant,
 *    the emulator is booted first.
 * 2. Pick the target device in the Flutter device selector: **Chrome (web)** or the Android emulator
 *    (**Medium Phone (mobile)**).
 * 3. Click **Run**. The plugin runs `flutter run` and the Run console shows the app building and starting.
 * 4. Change the home page title in `lib/main.dart` from "Flutter Demo Home Page" to "Hot Reloaded Home Page", save,
 *    and click **Hot Reload**.
 * 5. Stop the app.
 *
 * Expected result: the app starts on the device and shows "Flutter Demo Home Page" in its app bar. After the hot
 * reload, the console reports a hot reload ("Reloaded 1 of ... libraries", or "Reloaded application in ..." on
 * Chrome; not a restart), and the running app
 * shows "Hot Reloaded Home Page" instead, without restarting. Then the app stops.
 *
 * Implementation notes:
 * The device is chosen with `FLUTTER_TEST_DEVICE`:
 * - `chrome` (default): selects "Chrome (web)" in the device selector. Chrome opens on the test's display.
 * - `emulator`: boots the Android AVD named by `FLUTTER_TEST_AVD` (default `Medium_Phone`) on the test's display
 *   before the IDE starts, and selects it. Needs a working Android toolchain (SDK licenses accepted).
 *
 * The test checks what the app displays by connecting to its Dart VM service (the URI the plugin prints as "Debug
 * service listening on ...") and reading the widget tree with `ext.flutter.debugDumpApp`. It checks the old title is
 * there before the edit, so the check is known to see the title, then waits for the new title to replace it.
 */
class RunAppAndHotReloadUITest : ExistingProjectUITest("run_app_test") {

  private val deviceKind = System.getenv("FLUTTER_TEST_DEVICE") ?: "chrome"
  private val emulator = AndroidEmulator()

  override fun prepareProject() {
    if (deviceKind == "emulator") emulator.start(projectDir.parent.toFile())
  }

  @AfterAll
  fun stopEmulator() = emulator.stop()

  @Test
  fun runEditAndHotReload() {
    startIdeOnProject()
    step("Open lib/main.dart", "The editor shows the counter app's `lib/main.dart`; the toolbar has the Flutter device selector and the `main.dart` run configuration.")
    openProjectFile("lib/main.dart")

    inFrame {
      step(
        "Select the device",
        if (deviceKind == "emulator") "The device selector popup lists the running emulator as \"Medium Phone (mobile)\"; after selecting it, the toolbar shows that device."
        else "The device selector popup lists Chrome (web), this computer's desktop device (Linux, macOS, or Windows), and any emulators; after selecting Chrome, the toolbar shows \"Chrome (web)\"."
      )
      if (deviceKind == "emulator") {
        selectDevice(*emulator.deviceSelectorNames.toTypedArray())
      }
      else {
        selectDevice("Chrome")
      }

      step(
        "Run the app",
        "The Run tool window opens a `main.dart` tab and shows `flutter run` output (\"Launching lib/main.dart ...\") until the app is up and a \"Debug service listening\" or VM Service line appears."
      )
      driver.invokeAction("Run")
      // The plugin prints this once the app is up on any device, with the VM service URI.
      var vmServiceUri: String? = null
      waitUntil("the app to start and print its VM service URI", timeout = if (deviceKind == "emulator") 15.minutes else 8.minutes) {
        vmServiceUri = vmServiceUriFromConsole(allEditorText())
        vmServiceUri != null
      }
      println("VM service: $vmServiceUri")

      val app = vmServiceUri!!
      // Baseline: the running app shows the original title. This proves the check below can see the title.
      waitForAppTitle(app, shows = OLD_TITLE, notShows = NEW_TITLE)

      val editor = editorFor("main.dart")
      step("Change the title", "In `lib/main.dart`, the home page title changes from \"$OLD_TITLE\" to \"$NEW_TITLE\". The app on the device still shows the old title.")
      editor.text = editor.text.replace(OLD_TITLE, NEW_TITLE)
      editor.moveCaretToText(NEW_TITLE)
      editor.scrollToCaret()
      driver.invokeAction("SaveAll")

      step(
        "Hot reload",
        "The console shows \"Performing hot reload...\" then \"Reloaded 1 of ... libraries\" (on Chrome: \"Reloaded application in ...\"); the app bar on the device changes to \"$NEW_TITLE\" and the counter keeps its value."
      )
      driver.invokeAction("Flutter.Toolbar.ReloadAction")
      waitForHotReload()
      waitForAppTitle(app, shows = NEW_TITLE, notShows = OLD_TITLE)

      // Leave time for the reloaded app to render in the recording.
      Thread.sleep(5_000)
      step("Stop the app", "The run session ends; the Stop button is disabled and the console shows the app has finished.")
      stopApp()
    }
  }

  /**
   * Waits until the console reports a hot reload that reloaded at least one library. Fails at once if the plugin
   * restarted the app instead, or the reload failed.
   */
  private fun IdeaFrameUI.waitForHotReload() {
    var console = ""
    waitUntil("the console to report a hot reload", timeout = 3.minutes) {
      console = allEditorText()
      FAILED_RELOAD_TEXTS.firstOrNull { console.contains(it) }?.let { throw AssertionError("Hot reload did not succeed: the console shows \"$it\"") }
      SUCCESSFUL_RELOAD.containsMatchIn(console)
    }
    println("Console: ${SUCCESSFUL_RELOAD.find(console)?.value}")
  }

  /**
   * Waits until the running app's widget tree contains a `Text` with [shows] and none with [notShows]. Connects to the
   * VM service at [vmServiceUri] on each try: a web app's debug connection is re-established during a hot reload.
   */
  private fun waitForAppTitle(vmServiceUri: String, shows: String, notShows: String) {
    var tree = ""
    try {
      waitUntil("the app to show \"$shows\"", timeout = 1.minutes) {
        tree = runCatching { VmServiceClient(vmServiceUri).use { it.widgetTreeDump() } }.getOrElse { "" }
        tree.contains("\"$shows\"") && !tree.contains("\"$notShows\"")
      }
    }
    catch (e: Exception) {
      val titles = Regex("Text\\(\"[^\"]*Home Page[^\"]*\"").findAll(tree).map { it.value }.distinct().toList()
      throw AssertionError("The running app does not show \"$shows\" (titles in its widget tree: $titles)", e)
    }
    println("The running app shows \"$shows\"")
  }

  private companion object {
    const val OLD_TITLE = "Flutter Demo Home Page"
    const val NEW_TITLE = "Hot Reloaded Home Page"

    /**
     * "Reloaded 1 of 650 libraries ..." (devices) with at least one library, or "Reloaded application in ..." (web).
     * "Reloaded 0 of ..." means the change wasn't sent, and is rejected by [FAILED_RELOAD_TEXTS].
     */
    val SUCCESSFUL_RELOAD = Regex("Reloaded ([1-9]\\d* of \\d+ librar|application in)")

    val FAILED_RELOAD_TEXTS = listOf("Restarted application", "Hot reload failed", "Reloaded 0 of", "Hot reload rejected")
  }
}
