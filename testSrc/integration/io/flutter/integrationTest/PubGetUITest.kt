/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.driver.sdk.invokeAction
import org.junit.jupiter.api.Test
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.minutes

/**
 * Adds a dependency to `pubspec.yaml` in the editor and runs the plugin's "Flutter Pub Get" action.
 */
class PubGetUITest : ExistingProjectUITest("pub_get_test") {

  @Test
  fun addDependencyAndRunPubGet() {
    startIdeOnProject()
    openProjectFile("pubspec.yaml")

    inFrame {
      val editor = editorFor("pubspec.yaml")
      // `args` is not a transitive dependency of a new app, so it only shows up in pubspec.lock after pub get.
      editor.text = editor.text.replaceFirst("\ndependencies:\n", "\ndependencies:\n  args: any\n")
      driver.invokeAction("SaveAll")
      driver.invokeAction("flutter.pub.get")
      waitForConsoleText("exit code 0", "Got dependencies", timeout = 3.minutes)
    }

    waitUntil("pubspec.lock to contain args") {
      projectDir.resolve("pubspec.lock").readText().contains("\n  args:")
    }
  }
}
