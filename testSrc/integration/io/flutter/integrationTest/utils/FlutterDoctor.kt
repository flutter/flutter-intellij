/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest.utils

import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.dialogs.terminal
import com.intellij.driver.sdk.ui.components.elements.jbTerminalPanel
import com.intellij.driver.sdk.waitFor
import java.awt.event.KeyEvent
import kotlin.time.Duration.Companion.seconds

/**
 * Runs `flutter doctor` in the IDE's built-in terminal and returns the Flutter version string.
 *
 * This verifies the Flutter version as the IDE sees it (i.e. via the SDK configured in
 * Languages & Frameworks > Flutter), not just what is on the system PATH.
 *
 * Must be called from within an [IdeaFrameUI] context.
 *
 * @return the Flutter version string, e.g. "3.24.5"
 * @throws IllegalStateException if the version cannot be parsed from the output.
 */
fun IdeaFrameUI.runFlutterDoctorAndGetVersion(): String {
  terminal {
    waitFound()
    click()

    // Type the command and press Enter to execute it.2
    keyboard {
      typeText("flutter doctor")
      key(KeyEvent.VK_ENTER)
    }

    // Wait up to 60 seconds for flutter doctor to finish.
    // "Doctor summary" always appears at the end of the output.
    waitFor(timeout = 60.seconds) { hasSubtext("Doctor summary") }
  }

  // Read the full terminal scrollback buffer to find the version line.
  // Flutter doctor output contains a line like:
  //   [✓] Flutter (Channel stable, 3.24.5, on macOS ...)
  val terminalText = jbTerminalPanel().text
  System.out.println("terminalTest:" + terminalText)

  val parsedText = parseFlutterVersion(terminalText)

  System.out.println("parsedTest:" + parsedText)
  return parsedText
}

/**
 * Parses the Flutter version from `flutter doctor` output.
 *
 * Looks for a line matching: Flutter (Channel <channel>, <version>, ...
 */
internal fun parseFlutterVersion(doctorOutput: String): String {
  val match = Regex("""Flutter \(Channel \w+, (\d+\.\d+\.\d+)""").find(doctorOutput)
  return checkNotNull(match?.groupValues?.get(1)) {
    "Could not parse Flutter version from flutter doctor output:\n$doctorOutput"
  }
}
