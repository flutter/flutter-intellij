/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.toolwindows.StripeButtonUi
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowRightToolbarUi
import com.intellij.driver.sdk.ui.components.common.toolWindow
import io.flutter.integrationTest.utils.AndroidEmulator
import io.flutter.integrationTest.utils.BrowserEngine
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

// TODO(radcliffc): This test doesn't yet verify the test case's pass condition (DevTools loads with no license errors).
//  - JxBrowser needs a license key; local builds don't have one, so `FLUTTER_TEST_BROWSER=jxbrowser` only checks the
//    "license could not be found" fallback (passes).
//  - JCEF (the `auto` default without a license) fails on IntelliJ 2026.2 with
//    `NoClassDefFoundError: com/intellij/ui/jcef/JBCefBrowser`: the plugin doesn't depend on `com.intellij.modules.jcef`.
//  - Walkthrough: the emulator window is mostly hidden behind the IDE, and the emulator's "nested virtualization"
//    dialog plus IDE notifications ("Input methods disabler", "Flutter supports hot reload!") stay on screen.
/**
 * **Shows:** debugging a Flutter app and opening the Flutter Inspector and Flutter DevTools tool windows, which embed
 * DevTools in a browser inside the IDE (TestTracker test case 1.2.2, "DevTools & Inspector").
 *
 * Steps:
 * 1. IntelliJ IDEA opens an existing Flutter app (created with `flutter create`). For the Android emulator variant,
 *    the emulator is booted first.
 * 2. Pick the target device in the Flutter device selector: the Android emulator (**Medium Phone (mobile)**) or
 *    **Chrome (web)**.
 * 3. Click **Debug** and wait until the app is running on the device.
 * 4. Click **Flutter Inspector** on the right-hand tool window bar.
 * 5. Click **Flutter DevTools** on the same bar.
 * 6. Stop the app.
 *
 * Expected result: both tool windows show DevTools in the embedded browser: the Inspector with the app's widget tree,
 * and DevTools with its tabs. Neither shows a license, installation, or connection error.
 *
 * Implementation notes:
 * - `FLUTTER_TEST_DEVICE`: `emulator` (default) boots the AVD named by `FLUTTER_TEST_AVD` (default `Medium_Phone`);
 *   `chrome` uses Chrome.
 * - `FLUTTER_TEST_BROWSER`: `auto` (default) uses JxBrowser when the plugin under test has a JxBrowser license key
 *   (local builds normally don't) and JCEF otherwise; `jxbrowser` or `jcef` forces one. With `jxbrowser` and no
 *   license, the test instead checks that the plugin shows its "license could not be found" fallback.
 * - The test checks the Swing side of each tool window: the embedded browser component is present and none of the
 *   plugin's error labels are. It can't see inside the web page; the walkthrough screenshots show the widget tree.
 */
class DevToolsInspectorUITest : ExistingProjectUITest("devtools_inspector") {

  private val deviceKind = System.getenv("FLUTTER_TEST_DEVICE") ?: "emulator"
  private val emulator = AndroidEmulator()

  override fun prepareProject() {
    if (deviceKind == "emulator") emulator.start(projectDir.parent.toFile())
  }

  @AfterAll
  fun stopEmulator() = emulator.stop()

  @Test
  fun openInspectorAndDevTools() {
    startIdeOnProject()
    val engine = BrowserEngine.fromEnvironment()
    engine.applyTo(driver)
    val expectLicenseFallback = engine == BrowserEngine.JXBROWSER && !BrowserEngine.pluginHasJxBrowserLicense()

    step("Open lib/main.dart", "The editor shows the counter app's `lib/main.dart`; the toolbar has the Flutter device selector and the `main.dart` run configuration.")
    openProjectFile("lib/main.dart")

    inFrame {
      step(
        "Select the device",
        if (deviceKind == "emulator") "The device selector popup lists the running emulator as \"Medium Phone (mobile)\"; after selecting it, the toolbar shows that device."
        else "The device selector popup lists Chrome (web) and any other devices; after selecting Chrome, the toolbar shows \"Chrome (web)\"."
      )
      if (deviceKind == "emulator") selectDevice(*emulator.deviceSelectorNames.toTypedArray()) else selectDevice("Chrome")

      step(
        "Debug the app",
        "The Debug tool window opens a `main.dart` tab with `flutter run` output; once the app is up on the device, the console shows a \"Dart VM Service\" or \"Debug service listening\" line."
      )
      driver.invokeAction("Debug")
      waitForConsoleText("Flutter run key commands", "A Dart VM Service", "Debug service listening",
                         timeout = if (deviceKind == "emulator") 15.minutes else 8.minutes)

      val browserExpectation =
        if (expectLicenseFallback) "Without a JxBrowser license, it shows \"The JxBrowser license could not be found.\" and an \"Open DevTools in the browser?\" link."
        else "It briefly shows \"Installing DevTools...\" or \"Loading...\", then DevTools loads in the embedded ${engine.displayName} browser."

      step(
        "Open the Flutter Inspector",
        "Clicking **Flutter Inspector** on the right-hand tool window bar opens it. $browserExpectation The Inspector shows the app's widget tree (`MyApp`, `MaterialApp`, `MyHomePage`, ...)."
      )
      openRightToolWindow(INSPECTOR)
      checkEmbeddedBrowser(INSPECTOR, engine, expectLicenseFallback)
      // The web page keeps loading after the browser component appears; give it time to render for the recording.
      Thread.sleep(10_000)

      step(
        "Open Flutter DevTools",
        "Clicking **Flutter DevTools** on the right-hand tool window bar opens it in place of the Inspector. $browserExpectation DevTools shows its tabs (Performance, CPU Profiler, Memory, Network, Logging, ...)."
      )
      openRightToolWindow(DEVTOOLS)
      checkEmbeddedBrowser(DEVTOOLS, engine, expectLicenseFallback)
      Thread.sleep(10_000)

      step("Stop the app", "The debug session ends; the Stop button is disabled and the console shows the app has finished.")
      stopApp()
    }
  }

  /**
   * Opens the tool window [id] the way a user would: by clicking its button on the right-hand tool window bar. Falls
   * back to its Activate action if the button isn't on the bar. Does nothing if the window is already showing, since
   * clicking the button would hide it.
   */
  private fun IdeaFrameUI.openRightToolWindow(id: String) {
    val button = xx(ToolWindowRightToolbarUi::class.java) { byClass("ToolWindowRightToolbar") }.list().firstOrNull()
      ?.stripeButton(id)
    if (button != null && button.present()) {
      if (!button.isToolWindowVisible()) button.click()
    }
    else {
      println("No '$id' button on the right tool window bar; activating the tool window with its action")
      val actionId = driver.utility(StripeButtonUi.ActivateToolWindowActionManager::class).getActionIdForToolWindow(id)
      driver.invokeAction(actionId)
    }
  }

  /**
   * Waits until the tool window [id] shows the embedded browser, and fails as soon as it shows one of the plugin's
   * error labels. With [expectLicenseFallback], waits for the "license could not be found" label instead.
   */
  private fun IdeaFrameUI.checkEmbeddedBrowser(id: String, engine: BrowserEngine, expectLicenseFallback: Boolean) {
    val window = toolWindow(id)
    var error: String? = null
    var texts = ""
    try {
      waitUntil("$id to show ${if (expectLicenseFallback) "the JxBrowser license fallback" else "the embedded ${engine.displayName} browser"}",
                timeout = 3.minutes) {
        texts = runCatching { window.getAllTexts().joinToString(" ") { it.text } }.getOrDefault("")
        if (expectLicenseFallback) return@waitUntil texts.contains(LICENSE_MISSING)
        error = ERROR_TEXTS.firstOrNull { texts.contains(it) }
        error != null || window.x(engine.componentXpath).present()
      }
    }
    catch (e: Exception) {
      throw AssertionError("$id never showed the expected content. Last text shown: \"$texts\"", e)
    }
    error?.let { throw AssertionError("$id shows an error: \"$it\"") }
    println("$id: " + if (expectLicenseFallback) "shows the JxBrowser license fallback" else "shows the embedded ${engine.displayName} browser")
  }

  private val BrowserEngine.componentXpath: String
    get() = when (this) {
      BrowserEngine.JXBROWSER -> "//div[contains(@javaclass, 'com.teamdev.jxbrowser')]"
      BrowserEngine.JCEF -> "//div[contains(@javaclass, 'com.intellij.ui.jcef')]"
    }

  private companion object {
    const val INSPECTOR = "Flutter Inspector"
    const val DEVTOOLS = "Flutter DevTools"
    const val LICENSE_MISSING = "The JxBrowser license could not be found"

    /** Labels the plugin shows in these tool windows when the embedded browser or DevTools can't be shown. */
    val ERROR_TEXTS = listOf(
      LICENSE_MISSING,
      "JxBrowser installation failed",
      "JxBrowser installation timed out",
      "The JxBrowser installation failed unexpectedly",
      "The embedded browser failed to load",
      "Setting up DevTools failed",
      "Flutter DevTools start-up failed",
      "Flutter DevTools does not exist",
      "No running applications",
    )
  }
}
