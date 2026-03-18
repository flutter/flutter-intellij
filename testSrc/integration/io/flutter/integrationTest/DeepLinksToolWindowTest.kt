/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.toolWindow
import com.intellij.driver.sdk.ui.components.common.toolwindows.projectView
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.junit5.config.UseLatestDownloadedIdeBuild
import com.intellij.ide.starter.project.LocalProjectInfo
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Paths
import kotlin.time.Duration.Companion.minutes
import io.flutter.integrationTest.utils.createFlutterProjectWithCli

@Tag("ui")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(UseLatestDownloadedIdeBuild::class)
class DeepLinksToolWindowTest {

  private val testProjectName = "my_deep_links_test_project_${System.currentTimeMillis()}"
  private lateinit var run: BackgroundRun

  @BeforeAll
  fun setup() {
    createFlutterProjectWithCli(testProjectName, projectName = "deep_links_test")
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

  @AfterAll
  fun cleanUpTestFolder() {
    // Clean up the project folder
    if (testProjectName.isNotEmpty()) {
      val projectPath = Paths.get(System.getProperty("java.io.tmpdir"), testProjectName)
      val projectFile = projectPath.toFile()
      if (projectFile.exists()) {
        projectFile.deleteRecursively()
        println("Successfully deleted test folder: $projectPath")
      } else {
        println("Test folder does not exist, skipping cleanup: $projectPath")
      }
    }
  }

  @Test
  fun testDeepLinksToolWindow() {
    println("Initializing IDE test context")
    val projectPath = Paths.get(System.getProperty("java.io.tmpdir"), testProjectName)
    run = Setup.setupTestContextIC("DeepLinksToolWindowTest", LocalProjectInfo(projectPath)).runIdeWithDriver()

    run.driver.withContext {
      ideFrame {
        waitFound()
        driver.waitForIndicators(5.minutes)
        println("IDE is ready, accessing editor.")

        // Make sure indexing is finished and project is healthy
        projectView {
          projectViewTree.pathExists("deep_links_test", "pubspec.yaml")
        }

        // Open the Deep Links Tool Window
        driver.invokeAction("ActivateFlutterDeepLinksToolWindow")

        // Wait for the tool window to be focused or exist
        toolWindow(io.flutter.deeplinks.DeepLinksViewFactory.TOOL_WINDOW_ID) {
          waitFound(1.minutes)

          // Verify a JCEF or JxBrowser component exists. We use 'Browser' in class name robustly.
          x("//div[contains(@class, 'Browser')]").waitFound(1.minutes)
        }
      }
    }
  }
}
