/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.welcomeScreen
import com.intellij.driver.sdk.ui.components.settings.settingsDialog
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.junit5.config.UseLatestDownloadedIdeBuild
import io.flutter.integrationTest.utils.FlutterTestSdk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.time.Duration.Companion.seconds

@Tag("ui")
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@ExtendWith(UseLatestDownloadedIdeBuild::class)
class FlutterSdkSettingsUITest {

  private lateinit var run: BackgroundRun
  private lateinit var alternateSdkPath: String

  @BeforeEach
  fun initContext() {
    alternateSdkPath = FlutterTestSdk.ensureInstalled()
    println("Alternate Flutter SDK path: $alternateSdkPath")
  }

  @AfterEach
  fun closeIde() {
    if (::run.isInitialized) {
      println("Closing IDE")
      run.closeIdeAndWait()
    } else {
      println("IDE was not started, skipping close")
    }
  }

  @Test
  fun openSettingsAndQuit() {
    println("Initializing IDE test context")
    run = Setup.setupTestContextIC("FlutterSdkSettingsUITest").runIdeWithDriver()

    run.driver.withContext {
      welcomeScreen {
        waitFound()
        println("Welcome screen is visible.")

        // Open Settings using the built-in SDK action (no project needed).
        openSettingsDialog()

        settingsDialog {
          waitFound(20.seconds)
          println("Settings dialog is open.")

          // Close without making changes.
          x("//div[@text='Cancel']").waitFound().click()
          println("Settings dialog closed.")
        }
      }
    }
  }
}
