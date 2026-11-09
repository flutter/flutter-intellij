/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest.utils

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Boots and stops an Android emulator for UI tests that run a Flutter app on a device.
 *
 * The AVD is named by `FLUTTER_TEST_AVD` (default `Medium_Phone`); the SDK is [Platform.androidSdk]. On Linux the
 * emulator window opens on the test's `DISPLAY`, so it shows up in recordings.
 */
class AndroidEmulator(
  val avdName: String = System.getenv("FLUTTER_TEST_AVD") ?: "Medium_Phone",
) {
  private var process: Process? = null
  private val adb get() = Platform.adb

  /**
   * Names the IDE's device selector may show for this emulator. A running AVD is listed by its name (for example
   * "Medium Phone (mobile)"); Flutter's own name for it is "sdk gphone...".
   */
  val deviceSelectorNames: List<String> get() = listOf(avdName.replace('_', ' '), "sdk gphone")

  /** Starts the emulator and waits until Android has finished booting. Logs go to `emulator-<avd>.log` in [logDir]. */
  fun start(logDir: File) {
    println("Starting emulator $avdName (display: ${Platform.displayMode.description})")
    WalkthroughSteps.step("Boot the Android emulator", "The Android emulator window for `$avdName` opens and boots to the Android home screen.")
    val pb = ProcessBuilder(
      Platform.androidEmulator, "-avd", avdName,
      "-no-snapshot-save", "-no-boot-anim", "-no-audio", "-gpu", "swiftshader_indirect",
      // A previous unclean exit would otherwise show a crash-report dialog that blocks boot.
      "-crash-report-mode", "disabled", "-no-metrics",
    ).redirectErrorStream(true).redirectOutput(File(logDir, "emulator-$avdName.log"))
    if (Platform.displayMode == Platform.DisplayMode.LINUX_X11) {
      pb.environment()["QT_QPA_PLATFORM"] = "xcb"
      pb.environment()["XDG_SESSION_TYPE"] = "x11"
    }
    process = pb.start()

    run(5, TimeUnit.MINUTES, adb, "wait-for-device") ?: error("Emulator $avdName did not appear in adb")
    val deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)
    while (System.currentTimeMillis() < deadline) {
      val booted = run(10, TimeUnit.SECONDS, adb, "shell", "getprop", "sys.boot_completed")?.trim()
      if (booted == "1") {
        println("Emulator booted")
        return
      }
      Thread.sleep(3_000)
    }
    error("Emulator $avdName did not finish booting")
  }

  /** Stops the emulator cleanly with `adb emu kill`, so its next boot doesn't wait on a crash-report dialog. */
  fun stop() {
    process?.let {
      runCatching { run(30, TimeUnit.SECONDS, adb, "emu", "kill") }
      it.destroy()
    }
    process = null
  }

  /**
   * Runs [command] and returns its output, or null if it doesn't finish within [timeout], in which case it is killed.
   * Output is read after the process exits, so a hung command can't block the read; the commands used here print only
   * a few bytes, well under the pipe buffer.
   */
  private fun run(timeout: Long, unit: TimeUnit, vararg command: String): String? {
    val process = ProcessBuilder(*command).redirectErrorStream(true).start()
    if (!process.waitFor(timeout, unit)) {
      process.destroyForcibly()
      return null
    }
    return process.inputStream.bufferedReader().use { it.readText() }
  }
}
