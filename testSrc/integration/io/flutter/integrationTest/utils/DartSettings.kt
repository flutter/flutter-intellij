/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest.utils

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.ui.components.elements.checkBox
import com.intellij.driver.sdk.ui.components.settings.settingsDialog
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.seconds

/** Project property behind the Dart plugin's "Turn on experimental LSP features" checkbox (`DartConfigurable`, default true). */
const val DART_EXPERIMENTAL_LSP_KEY = "dart.lsp.experimental.enabled"

/**
 * Sets "Turn on experimental LSP features" in **Settings > Languages & Frameworks > Dart** the way a user does: opens
 * Settings, checks or unchecks the box, and clicks Apply and OK. Fails if the box doesn't end up in the requested state.
 */
fun IdeaFrameUI.setDartExperimentalLspFeatures(enabled: Boolean) {
  openSettingsDialog()
  settingsDialog {
    waitFound(30.seconds)
    openTreeSettingsSection("Languages & Frameworks", "Dart")
    val flag = checkBox { contains(byVisibleText("Turn on experimental LSP features")) }
    flag.waitFound(30.seconds)
    println("Experimental LSP features checkbox was ${if (flag.isSelected()) "on" else "off"}; setting it ${if (enabled) "on" else "off"}")
    if (flag.isSelected() != enabled) flag.click()
    check(flag.isSelected() == enabled) { "Could not set \"Turn on experimental LSP features\" to $enabled" }
    applyButton.click()
    button("OK").click()
  }
}

/** The value the Dart plugin reads for "Turn on experimental LSP features" in the open project. */
fun Driver.dartExperimentalLspFeaturesEnabled(): Boolean =
  service(ProjectPropertiesRef::class, singleProject()).getBoolean(DART_EXPERIMENTAL_LSP_KEY, true)

@Remote("com.intellij.ide.util.PropertiesComponent")
internal interface ProjectPropertiesRef {
  fun getBoolean(name: String, defaultValue: Boolean): Boolean
}

/** Static methods of the Dart plugin's `DartAnalysisServerService`. */
@Remote("com.jetbrains.lang.dart.analyzer.DartAnalysisServerService", plugin = "Dart")
interface DartAnalysisServerServiceStatics {
  /** Experimental LSP features are on and the Dart SDK is new enough for LSP highlighting (dart-intellij-third-party#640). */
  fun isLspHighlightingEnabled(project: Project): Boolean

  /** [isLspHighlightingEnabled], and the analysis server advertised semantic tokens, so legacy highlighting is off. */
  fun isLspHighlightingActive(project: Project): Boolean
}

/** The Dart SDK version of `FLUTTER_SDK` (from `bin/cache/dart-sdk/version`), for example `3.14.0-315.3.beta`. */
fun flutterDartSdkVersion(): String {
  val versionFile = Platform.flutterSdk.resolve("bin/cache/dart-sdk/version")
  check(versionFile.exists()) { "No Dart SDK in $versionFile; run `flutter --version` with FLUTTER_SDK=${Platform.flutterSdk} once" }
  return versionFile.readText().trim()
}

/**
 * Fails with a clear message if `FLUTTER_SDK`'s Dart SDK is older than [minimum], which [feature] needs. Call it before
 * starting the IDE, so a too-old SDK doesn't show up later as a confusing assertion failure.
 */
fun checkDartSdkAtLeast(minimum: String, feature: String) {
  val version = flutterDartSdkVersion()
  println("Dart SDK: $version")
  check(compareDartVersions(version, minimum) >= 0) {
    "$feature needs Dart SDK $minimum or newer; FLUTTER_SDK=${Platform.flutterSdk} has $version (for example use the Flutter 3.50 beta)"
  }
}

/** Compares Dart SDK versions like `3.14.0-315.3.beta` and `3.14.0-307.0.dev` (a release beats its prereleases). */
fun compareDartVersions(a: String, b: String): Int {
  fun parse(v: String): Pair<List<Int>, List<Int>?> {
    val main = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    val pre = v.substringAfter('-', "").takeIf { it.isNotEmpty() }?.split('.')?.mapNotNull { it.toIntOrNull() }
    return main to pre
  }
  val (am, ap) = parse(a)
  val (bm, bp) = parse(b)
  for (i in 0 until maxOf(am.size, bm.size)) {
    val c = am.getOrElse(i) { 0 }.compareTo(bm.getOrElse(i) { 0 })
    if (c != 0) return c
  }
  if (ap == null || bp == null) return (if (ap == null) 1 else 0) - (if (bp == null) 1 else 0)
  for (i in 0 until maxOf(ap.size, bp.size)) {
    val c = ap.getOrElse(i) { 0 }.compareTo(bp.getOrElse(i) { 0 })
    if (c != 0) return c
  }
  return 0
}
