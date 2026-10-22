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
 * The AVD is named by `FLUTTER_TEST_AVD` (default `Medium_Phone`) and the SDK is found through `ANDROID_HOME`
 * (default `~/Android/Sdk`). The emulator window opens on the test's `DISPLAY`, so it shows up in recordings.
 */
class AndroidEmulator(
  val avdName: String = System.getenv("FLUTTER_TEST_AVD") ?: "Medium_Phone",
  private val androidHome: String = System.getenv("ANDROID_HOME") ?: "${System.getProperty("user.home")}/Android/Sdk",
) {
  private var process: Process? = null
  private val adb get() = "$androidHome/platform-tools/adb"

  /**
   * Names the IDE's device selector may show for this emulator. A running AVD is listed by its name (for example
   * "Medium Phone (mobile)"); Flutter's own name for it is "sdk gphone...".
   */
  val deviceSelectorNames: List<String> get() = listOf(avdName.replace('_', ' '), "sdk gphone")

  /** Starts the emulator and waits until Android has finished booting. Logs go to `emulator-<avd>.log` in [logDir]. */
  fun start(logDir: File) {
    println("Starting emulator $avdName on DISPLAY=${System.getenv("DISPLAY")}")
    WalkthroughSteps.step("Boot the Android emulator", "The Android emulator window for `$avdName` opens and boots to the Android home screen.")
    process = ProcessBuilder(
      "$androidHome/emulator/emulator", "-avd", avdName,
      "-no-snapshot-save", "-no-boot-anim", "-no-audio", "-gpu", "swiftshader_indirect",
      // A previous unclean exit would otherwise show a crash-report dialog that blocks boot.
      "-crash-report-mode", "disabled", "-no-metrics",
    ).redirectErrorStream(true).redirectOutput(File(logDir, "emulator-$avdName.log")).start()

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
    error("Emulator $avdName did not finish booting")
  }

  /** Stops the emulator cleanly with `adb emu kill`, so its next boot doesn't wait on a crash-report dialog. */
  fun stop() {
    process?.let {
      runCatching { exec(adb, "emu", "kill").waitFor(30, TimeUnit.SECONDS) }
      it.destroy()
    }
    process = null
  }

  private fun exec(vararg command: String): Process = ProcessBuilder(*command).redirectErrorStream(true).start()
}
