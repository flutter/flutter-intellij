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
 * **Shows:** adding a package dependency and fetching it with the Flutter plugin's **Flutter Pub Get** action.
 *
 * Steps:
 * 1. IntelliJ IDEA opens an existing Flutter app (created with `flutter create`).
 * 2. Open `pubspec.yaml` and add `args: any` under `dependencies:`.
 * 3. Save, then run **Flutter Pub Get**.
 * 4. The plugin runs `flutter pub get` and shows its output in the Messages console.
 *
 * Expected result: the console reports success (exit code 0) and `pubspec.lock` now lists the `args` package.
 */
class PubGetUITest : ExistingProjectUITest("pub_get_test") {

  @Test
  fun addDependencyAndRunPubGet() {
    startIdeOnProject()
    step("Open pubspec.yaml", "The editor shows the app's `pubspec.yaml`, with `flutter` and `cupertino_icons` under `dependencies:`.")
    openProjectFile("pubspec.yaml")

    inFrame {
      val editor = editorFor("pubspec.yaml")
      // `args` is not a transitive dependency of a new app, so it only shows up in pubspec.lock after pub get.
      step("Add the args dependency", "A new line `args: any` appears under `dependencies:` in `pubspec.yaml`.")
      editor.text = editor.text.replaceFirst("\ndependencies:\n", "\ndependencies:\n  args: any\n")
      driver.invokeAction("SaveAll")
      step(
        "Run Flutter Pub Get",
        "The Messages tool window opens and shows `flutter pub get` output, ending with \"Process finished with exit code 0\"."
      )
      driver.invokeAction("flutter.pub.get")
      waitForConsoleText("exit code 0", "Got dependencies", timeout = 3.minutes)
    }

    waitUntil("pubspec.lock to contain args") {
      projectDir.resolve("pubspec.lock").readText().contains("\n  args:")
    }
  }
}
