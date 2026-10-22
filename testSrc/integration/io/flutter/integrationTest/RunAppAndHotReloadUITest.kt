/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.driver.sdk.invokeAction
import io.flutter.integrationTest.utils.AndroidEmulator
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
 * Expected result: the app starts on the device, the console reports a successful hot reload
 * ("Reloaded ..."), and the app stops cleanly.
 *
 * Implementation notes:
 * The device is chosen with `FLUTTER_TEST_DEVICE`:
 * - `chrome` (default): selects "Chrome (web)" in the device selector. Chrome opens on the test's display.
 * - `emulator`: boots the Android AVD named by `FLUTTER_TEST_AVD` (default `Medium_Phone`) on the test's display
 *   before the IDE starts, and selects it. Needs a working Android toolchain (SDK licenses accepted).
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
      // Printed by `flutter run` once the app is up on any device.
      waitForConsoleText("Flutter run key commands", "A Dart VM Service", "Debug service listening",
                         timeout = if (deviceKind == "emulator") 15.minutes else 8.minutes)

      val editor = editorFor("main.dart")
      step("Change the title", "In `lib/main.dart`, the home page title changes from \"Flutter Demo Home Page\" to \"Hot Reloaded Home Page\".")
      editor.text = editor.text.replace("Flutter Demo Home Page", "Hot Reloaded Home Page")
      editor.moveCaretToText("Hot Reloaded Home Page")
      editor.scrollToCaret()
      driver.invokeAction("SaveAll")
      step("Hot reload", "The console shows \"Performing hot reload...\" followed by \"Reloaded ...\" (for example \"Reloaded application in 321ms\").")
      driver.invokeAction("Flutter.Toolbar.ReloadAction")
      waitForConsoleText("Reloaded", "Restarted", timeout = 3.minutes)

      // Leave time for the reloaded app to render in the recording.
      Thread.sleep(5_000)
      step("Stop the app", "The run session ends; the Stop button is disabled and the console shows the app has finished.")
      stopApp()
    }
  }
}
