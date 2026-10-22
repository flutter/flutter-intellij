/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.driver.sdk.invokeAction
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.minutes

/**
 * **Shows:** adding a package dependency and fetching it with the Flutter plugin's **Flutter Pub Get** action.
 *
 * The package is [`http`](https://pub.dev/packages/http) with the constraint `^1.2.0`: the Dart team's standard
 * package for HTTP requests, one of the most used packages on pub.dev, and not a dependency (direct or transitive)
 * of an app made by `flutter create`, so it can only appear after this test's Pub Get.
 *
 * Steps:
 * 1. IntelliJ IDEA opens an existing Flutter app (created with `flutter create`).
 * 2. Open `pubspec.yaml` and add `http: ^1.2.0` under `dependencies:`.
 * 3. Save, then run **Flutter Pub Get**.
 * 4. The plugin runs `flutter pub get` and shows its output in the Messages console.
 *
 * Expected result:
 * - The console reports success (exit code 0).
 * - `pubspec.lock` lists `http` as a `direct main` dependency from `hosted` pub.dev, at a 1.x version.
 * - `.dart_tool/package_config.json` maps `http` to the `http-<version>` folder of the pub cache, and that folder
 *   contains the downloaded package (`lib/http.dart`).
 */
class PubGetUITest : ExistingProjectUITest("pub_get_test") {

  private companion object {
    const val PACKAGE = "http"
    const val CONSTRAINT = "^1.2.0"
    const val MAJOR_VERSION = "1."
    const val FAKE_MAJOR_VERSION = "2."

  }

  @Test
  fun addDependencyAndRunPubGet() {
    check(lockedVersion() == null) { "$PACKAGE is already in pubspec.lock before Pub Get; pick a package a new app doesn't use" }

    startIdeOnProject()
    step("Open pubspec.yaml", "The editor shows the app's `pubspec.yaml`, with `flutter` and `cupertino_icons` under `dependencies:`.")
    openProjectFile("pubspec.yaml")

    inFrame {
      val editor = editorFor("pubspec.yaml")
      step("Add the http dependency", "A new line `$PACKAGE: $CONSTRAINT` appears under `dependencies:` in `pubspec.yaml`.")
      editor.text = editor.text.replaceFirst("\ndependencies:\n", "\ndependencies:\n  $PACKAGE: $CONSTRAINT\n")
      driver.invokeAction("SaveAll")
      step(
        "Run Flutter Pub Get",
        "The Messages tool window opens and shows `flutter pub get` output, ending with \"Process finished with exit code 0\"."
      )
      driver.invokeAction("flutter.pub.get")
      waitForConsoleText("exit code 0", "Got dependencies", timeout = 3.minutes)
    }

    var version = ""
    waitUntil("pubspec.lock to contain $PACKAGE") {
      version = lockedVersion() ?: ""
      version.isNotEmpty()
    }
    println("pubspec.lock: $PACKAGE $version")
    check(version.startsWith(MAJOR_VERSION)) { "Expected $PACKAGE $CONSTRAINT to resolve to a 1.x version, got $version" }

    // Added the following line to test for ensuring that the test can be broken
    // remove if desired.
    // check(version.startsWith(FAKE_MAJOR_VERSION)) { "Expected $PACKAGE $CONSTRAINT to resolve to a 1.x version, got $version" }

    val lockEntry = lockEntry()!!
    check(lockEntry.contains("dependency: \"direct main\"")) { "$PACKAGE is not a direct main dependency in pubspec.lock:\n$lockEntry" }
    check(lockEntry.contains("source: hosted")) { "$PACKAGE is not a hosted package in pubspec.lock:\n$lockEntry" }

    val packageRoot = cachedPackageRoot()
    println("package_config.json: $PACKAGE -> $packageRoot")
    check(packageRoot.name == "$PACKAGE-$version") { "Expected the pub cache folder $PACKAGE-$version, got $packageRoot" }
    check(packageRoot.isDirectory()) { "Pub cache folder $packageRoot does not exist" }
    check(packageRoot.resolve("lib").resolve("$PACKAGE.dart").exists()) { "$packageRoot does not contain lib/$PACKAGE.dart" }
  }

  /** The `PACKAGE:` block of `pubspec.lock`, or null if the package is not locked. */
  private fun lockEntry(): String? {
    val lockFile = projectDir.resolve("pubspec.lock")
    if (!lockFile.exists()) return null
    val lock = lockFile.readText().replace("\r\n", "\n")
    return Regex("\n  $PACKAGE:\n((?: {4}.*\n)+)").find(lock)?.groupValues?.get(1)
  }

  /** The version of [PACKAGE] in `pubspec.lock`, or null if it is not locked. */
  private fun lockedVersion(): String? =
    lockEntry()?.let { Regex("(?m)^ {4}version: \"([^\"]+)\"").find(it)?.groupValues?.get(1) }

  /** The folder that `.dart_tool/package_config.json` maps [PACKAGE] to; it must be an absolute location (the pub cache). */
  private fun cachedPackageRoot(): Path {
    val config = projectDir.resolve(".dart_tool").resolve("package_config.json").readText()
    val rootUri = Regex("\"name\":\\s*\"$PACKAGE\",\\s*\"rootUri\":\\s*\"([^\"]+)\"").find(config)?.groupValues?.get(1)
                  ?: throw IllegalStateException("$PACKAGE is not in .dart_tool/package_config.json")
    check(rootUri.startsWith("file:")) { "$PACKAGE maps to $rootUri, not to a folder in the pub cache" }
    return Paths.get(URI(rootUri))
  }
}
