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
