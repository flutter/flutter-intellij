/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest.utils

import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Everything in the UI tests that differs between Linux, macOS, and Windows.
 *
 * Tests must not check the OS themselves; add what they need here instead, so that every test runs unchanged on all
 * three platforms.
 */
object Platform {

  /** Where the IDE under test is displayed. */
  enum class DisplayMode(val description: String) {
    LINUX_STARTER_XVFB("hidden Xvfb display started by IDE Starter (DISPLAY is unset)"),
    LINUX_X11("X11 display (for example Xvfb or Xephyr from the walkthrough recorder)"),
    LINUX_WAYLAND_DESKTOP("Wayland desktop, through XWayland (input goes through the RemoteDesktop portal)"),
    MAC_DESKTOP("macOS desktop"),
    WINDOWS_DESKTOP("Windows desktop"),
  }

  val displayMode: DisplayMode
    get() = when {
      SystemInfo.isMac -> DisplayMode.MAC_DESKTOP
      SystemInfo.isWindows -> DisplayMode.WINDOWS_DESKTOP
      System.getenv("DISPLAY").isNullOrEmpty() -> DisplayMode.LINUX_STARTER_XVFB
      !System.getenv("WAYLAND_DISPLAY").isNullOrEmpty() -> DisplayMode.LINUX_WAYLAND_DESKTOP
      else -> DisplayMode.LINUX_X11
    }

  /** Path of the Flutter SDK, from `FLUTTER_SDK`. */
  val flutterSdk: Path
    get() = Paths.get(
      System.getenv("FLUTTER_SDK")
      ?: throw IllegalStateException("Set the FLUTTER_SDK environment variable to the Flutter SDK to test with")
    )

  /** The `flutter` command: `bin/flutter`, or `bin\flutter.bat` on Windows. */
  val flutterExecutable: String
    get() = flutterSdk.resolve("bin").resolve(if (SystemInfo.isWindows) "flutter.bat" else "flutter").toString()

  /**
   * The Android SDK: `ANDROID_HOME`, then `ANDROID_SDK_ROOT`, then the default location of Android Studio's SDK
   * manager on this OS.
   */
  val androidSdk: Path
    get() {
      System.getenv("ANDROID_HOME")?.takeIf { it.isNotEmpty() }?.let { return Paths.get(it) }
      System.getenv("ANDROID_SDK_ROOT")?.takeIf { it.isNotEmpty() }?.let { return Paths.get(it) }
      val home = System.getProperty("user.home")
      return when {
        SystemInfo.isMac -> Paths.get(home, "Library", "Android", "sdk")
        SystemInfo.isWindows -> Paths.get(System.getenv("LOCALAPPDATA") ?: Paths.get(home, "AppData", "Local").toString(), "Android", "Sdk")
        else -> Paths.get(home, "Android", "Sdk")
      }
    }

  /** The `adb` executable of [androidSdk]. */
  val adb: String
    get() = androidSdk.resolve("platform-tools").resolve(executableName("adb")).toString()

  /** The Android `emulator` executable of [androidSdk]. */
  val androidEmulator: String
    get() = androidSdk.resolve("emulator").resolve(executableName("emulator")).toString()

  /**
   * Parent folder for the Flutter projects that tests create.
   *
   * On Windows this is a short path (`%USERPROFILE%\ft`), because the temp folder is deep enough that Android and
   * Gradle builds inside a project can exceed the 260-character path limit. Elsewhere it is the temp folder with
   * symbolic links resolved (on macOS `/var/folders/...` is a link to `/private/var/folders/...`, and the IDE reports
   * the resolved path).
   */
  val testProjectsDir: Path
    get() {
      val dir = if (SystemInfo.isWindows) {
        Paths.get(System.getProperty("user.home"), "ft")
      }
      else {
        Paths.get(System.getProperty("java.io.tmpdir"))
      }
      Files.createDirectories(dir)
      return dir.toRealPath()
    }

  /**
   * Deletes [dir] and everything in it. On Windows, files can stay locked for a while after the IDE or Dart processes
   * exit, so this retries for up to [timeoutMillis]; if the folder still can't be deleted, it logs and returns false
   * instead of failing the test.
   */
  fun deleteRecursively(dir: File, timeoutMillis: Long = 30_000): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (true) {
      if (!dir.exists() || dir.deleteRecursively()) return true
      if (System.currentTimeMillis() >= deadline) {
        println("Could not delete $dir (files may still be in use); delete it by hand")
        return false
      }
      Thread.sleep(1_000)
    }
  }

  /** One line describing the platform, display, and SDKs, printed at the start of each test. */
  fun describe(): String =
    "OS: ${SystemInfo.OS_NAME} ${SystemInfo.OS_VERSION} (${SystemInfo.OS_ARCH}); display: ${displayMode.description}; " +
    "FLUTTER_SDK: ${System.getenv("FLUTTER_SDK") ?: "(not set)"}"

  private fun executableName(name: String) = if (SystemInfo.isWindows) "$name.exe" else name
}
