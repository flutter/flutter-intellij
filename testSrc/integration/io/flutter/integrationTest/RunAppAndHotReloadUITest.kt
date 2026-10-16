/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import java.awt.event.KeyEvent
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes

/**
 * Runs the app from the IDE on a device, edits `lib/main.dart`, hot reloads, and stops the app.
 *
 * Device is chosen with `FLUTTER_TEST_DEVICE`:
 * - `chrome` (default): selects "Chrome (web)" in the device selector. Chrome opens on the test's display.
 * - `emulator`: boots the Android AVD named by `FLUTTER_TEST_AVD` (default `Medium_Phone`) on the test's display
 *   before the IDE starts, and selects it. Needs a working Android toolchain (SDK licenses accepted).
 */
class RunAppAndHotReloadUITest : ExistingProjectUITest("run_app_test") {

  private val deviceKind = System.getenv("FLUTTER_TEST_DEVICE") ?: "chrome"
  private val androidHome = System.getenv("ANDROID_HOME") ?: "${System.getProperty("user.home")}/Android/Sdk"
  private var emulator: Process? = null

  override fun prepareProject() {
    if (deviceKind == "emulator") startEmulator()
  }

  @AfterAll
  fun stopEmulator() {
    emulator?.let {
      runCatching { exec("$androidHome/platform-tools/adb", "emu", "kill").waitFor(30, TimeUnit.SECONDS) }
      it.destroy()
    }
  }

  @Test
  fun runEditAndHotReload() {
    startIdeOnProject()
    openProjectFile("lib/main.dart")

    inFrame {
      selectDevice(if (deviceKind == "emulator") "sdk gphone" else "Chrome")

      driver.invokeAction("Run")
      // Printed by `flutter run` once the app is up on any device.
      waitForConsoleText("Flutter run key commands", "A Dart VM Service", "Debug service listening",
                         timeout = if (deviceKind == "emulator") 15.minutes else 8.minutes)

      val editor = editorFor("main.dart")
      editor.text = editor.text.replace("Flutter Demo Home Page", "Hot Reloaded Home Page")
      driver.invokeAction("SaveAll")
      driver.invokeAction("Flutter.Toolbar.ReloadAction")
      waitForConsoleText("Reloaded", "Restarted", timeout = 3.minutes)

      // Leave time for the reloaded app to render in the recording.
      Thread.sleep(5_000)
      stopApp()
    }
  }

  /** Stops the running app. The Stop action needs an IDE context, so focus the editor and use its shortcut. */
  private fun IdeaFrameUI.stopApp() {
    runCatching {
      editorFor("main.dart").click()
      keyboard { hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_F2) }
      Thread.sleep(3_000)
    }.onFailure { println("Could not stop the app (${it.message}); it stops when the IDE closes.") }
  }

  /** Opens the Flutter device selector and picks the device whose name starts with [name]. */
  private fun IdeaFrameUI.selectDevice(name: String) {
    val selector = x(
      "//div[@class='JBLabel' and (contains(@text, '(desktop)') or contains(@text, '(web)') or " +
      "contains(@text, '(mobile)') or contains(@text, 'device') or contains(@text, 'Device'))]"
    )
    waitUntil("device '$name' to be selected", timeout = 3.minutes) {
      if (selector.getAllTexts().any { it.text.startsWith(name, ignoreCase = true) }) return@waitUntil true
      selector.click()
      Thread.sleep(1_000)
      keyboard { typeText(name, 50) }
      Thread.sleep(1_000)
      // Only confirm when the popup's speed search matched a device; otherwise close the popup so the typed text
      // can't land in the editor, and retry once the device daemon has listed more devices.
      if (x("//div[contains(@visible_text, 'Nothing to show')]").present()) {
        keyboard { escape() }
      }
      else {
        keyboard { enter() }
      }
      Thread.sleep(2_000)
      selector.getAllTexts().any { it.text.startsWith(name, ignoreCase = true) }
    }
    println("Selected device: $name")
  }

  private fun startEmulator() {
    val avd = System.getenv("FLUTTER_TEST_AVD") ?: "Medium_Phone"
    println("Starting emulator $avd on DISPLAY=${System.getenv("DISPLAY")}")
    emulator = ProcessBuilder(
      "$androidHome/emulator/emulator", "-avd", avd,
      "-no-snapshot-save", "-no-boot-anim", "-no-audio", "-gpu", "swiftshader_indirect",
    ).redirectErrorStream(true).redirectOutput(File(projectDir.parent.toFile(), "emulator-$avd.log")).start()

    val adb = "$androidHome/platform-tools/adb"
    exec(adb, "wait-for-device").waitFor(5, TimeUnit.MINUTES)
    val deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)
    while (System.currentTimeMillis() < deadline) {
      val booted = exec(adb, "shell", "getprop", "sys.boot_completed").inputStream.bufferedReader().readText().trim()
      if (booted == "1") {
        println("Emulator booted")
        return
      }
      Thread.sleep(3_000)
    }
    error("Emulator $avd did not finish booting")
  }

  private fun exec(vararg command: String): Process = ProcessBuilder(*command).redirectErrorStream(true).start()
}
