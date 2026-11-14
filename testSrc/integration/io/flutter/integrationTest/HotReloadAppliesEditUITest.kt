/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.singleProject
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import io.flutter.integrationTest.utils.VmServiceClient
import io.flutter.integrationTest.utils.vmServiceUriFromConsole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

/**
 * Hot reload applies an edit to a running app: run the counter app on Chrome, change the home page title in
 * `lib/main.dart`, hot reload, and check the live app shows the new title after a reload (not a restart), then stop.
 *
 * Spec: `testSrc/integration/specs/hot-reload-applies-edit.md` (MVP test T1).
 */
@Tag("agent-generated")
class HotReloadAppliesEditUITest : ExistingProjectUITest("hot_reload_app") {

  @Test
  fun hotReloadAppliesEdit() {
    startIdeOnProject()
    openProjectFile("lib/main.dart")

    inFrame {
      step("1 Pick Chrome (web) in the device selector on the main toolbar") {
        selectDevice("Chrome")
      }

      step("2 Click Run next to the main.dart run configuration (not Debug)") {
        invokeFlutterAction("Run")
      }

      var vmServiceUri: String? = null
      step("3 Wait for the app to start: the Run console prints the VM service URI") {
        waitUntil("the app to start and print its VM service URI", timeout = 8.minutes) {
          vmServiceUri = vmServiceUriFromConsole(consoleText())
          vmServiceUri != null
        }
        println("VM service: $vmServiceUri")
      }
      val app = requireNotNull(vmServiceUri)

      assertOracle("O1", "console shows the app running with no reload yet; live app shows the old title; run config is Flutter's") {
        val console = consoleText()
        assertTrue(console.contains("Flutter run key commands") || console.contains("ws://"), "console does not show a running app")
        for (bad in listOf("Reloaded", "Restarted", "Performing hot")) {
          assertFalse(console.contains(bad), "console already contains '$bad' before the reload")
        }
        waitForAppTitle(app, shows = OLD_TITLE, notShows = NEW_TITLE)
        assertEquals("FlutterRunConfigurationType", selectedRunConfigurationTypeId(), "selected run configuration is not the Flutter plugin's")
      }

      step("4 In lib/main.dart, change 'Flutter Demo Home Page' to 'Hot Reloaded Home Page' in the MyHomePage(title:) argument; don't save") {
        val editor = editorFor("main.dart")
        val text = editor.text
        assertTrue(text.contains(OLD_TITLE), "main.dart does not contain '$OLD_TITLE'")
        editor.text = text.replace(OLD_TITLE, NEW_TITLE)
        editor.moveCaretToText(NEW_TITLE)
      }

      step("5 Click Flutter Hot Reload in the Run tool window toolbar", trigger = true) {
        invokeFlutterAction("Flutter.Toolbar.ReloadAction")
      }

      assertOracle("O2", "the reload action was enabled and visible; the plugin printed a hot-reload progress message") {
        // Enabled/visible: invokeFlutterAction in step 5 fails if the action was rejected, so reaching here proves it.
        // The plugin clears the console when the reload starts, so read the whole console, not an offset.
        waitForConsoleText("Performing hot", timeout = 3.minutes)
      }

      assertOracle("O3", "console reports a successful reload; live app shows the new title; editor keeps the edit") {
        waitUntil("the console to report a successful reload", timeout = 3.minutes) {
          val console = consoleText()
          FAILED_RELOAD_TEXTS.firstOrNull { console.contains(it) }?.let { throw AssertionError("Hot reload did not succeed: the console shows \"$it\"") }
          SUCCESSFUL_RELOAD.containsMatchIn(console)
        }
        waitForAppTitle(app, shows = NEW_TITLE, notShows = OLD_TITLE)
        assertTrue(editorFor("main.dart").text.contains(NEW_TITLE), "the editor no longer shows the edited title")
      }

      assertOracle("O4", "no restart, no failed or empty reload, no relaunch after the trigger") {
        val console = consoleText()
        for (bad in FAILED_RELOAD_TEXTS) {
          assertFalse(console.contains(bad), "console shows '$bad' after the reload")
        }
        assertFalse(console.contains("Flutter run key commands"), "the app was relaunched after the reload")
      }

      step("6 Click Stop in the Run tool window") {
        stopAppAndVerifyTerminated()
      }
    }
  }

  /**
   * Waits until the running app's widget tree contains a `Text` with [shows] and none with [notShows]. Connects to the
   * VM service at [vmServiceUri] on each try: a web app's debug connection is re-established during a hot reload.
   */
  private fun waitForAppTitle(vmServiceUri: String, shows: String, notShows: String) {
    var tree = ""
    try {
      waitUntil("the app to show \"$shows\"", timeout = 1.minutes) {
        tree = runCatching { VmServiceClient(vmServiceUri).use { it.widgetTreeDump() } }.getOrElse { "" }
        tree.contains("\"$shows\"") && !tree.contains("\"$notShows\"")
      }
    }
    catch (e: Exception) {
      val titles = Regex("Text\\(\"[^\"]*Home Page[^\"]*\"").findAll(tree).map { it.value }.distinct().toList()
      throw AssertionError("The running app does not show \"$shows\" (titles in its widget tree: $titles)", e)
    }
    println("The running app shows \"$shows\"")
  }

  private fun IdeaFrameUI.selectedRunConfigurationTypeId(): String? {
    val settings = driver.service(RunManagerRef::class, driver.singleProject()).getSelectedConfiguration()
    assertNotNull(settings, "no run configuration is selected")
    return settings!!.getType().getId()
  }

  @Remote("com.intellij.execution.RunManager")
  interface RunManagerRef {
    fun getSelectedConfiguration(): RunnerAndConfigurationSettingsRef?
  }

  @Remote("com.intellij.execution.RunnerAndConfigurationSettings")
  interface RunnerAndConfigurationSettingsRef {
    fun getType(): ConfigurationTypeRef
    fun getName(): String
  }

  @Remote("com.intellij.execution.configurations.ConfigurationType")
  interface ConfigurationTypeRef {
    fun getId(): String
  }

  private companion object {
    const val OLD_TITLE = "Flutter Demo Home Page"
    const val NEW_TITLE = "Hot Reloaded Home Page"

    /** "Reloaded 1 of 650 libraries ..." (devices) with at least one library, or "Reloaded application in ..." (web). */
    val SUCCESSFUL_RELOAD = Regex("Reloaded ([1-9]\\d* of \\d+ librar|application in)")

    val FAILED_RELOAD_TEXTS = listOf("Performing hot restart", "Restarted application", "Hot reload failed", "Reloaded 0 of", "Hot reload rejected")
  }
}
