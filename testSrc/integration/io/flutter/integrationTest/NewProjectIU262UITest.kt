/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import io.flutter.integrationTest.utils.deleteFlutterProject
import io.flutter.integrationTest.utils.newProjectWelcomeScreen
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.time.Duration.Companion.minutes

/**
 * Launches IntelliJ IDEA 2026.2.3 with the locally built plugin and creates a new Flutter app
 * through the New Project wizard.
 *
 * Requires the `FLUTTER_SDK` environment variable. The created project in `~/IdeaProjects` is deleted after the
 * test; set `KEEP_TEST_PROJECT=true` to keep it. Set `KEEP_IDE_OPEN_MINUTES` to leave the IDE running for manual
 * inspection.
 */
@Tag("ui")
class NewProjectIU262UITest {

  private lateinit var run: BackgroundRun
  private var testProjectName = ""

  @AfterEach
  fun closeIdeAndCleanUp() {
    if (::run.isInitialized) {
      val keepOpenMinutes = System.getenv("KEEP_IDE_OPEN_MINUTES")?.toLongOrNull() ?: 0L
      if (keepOpenMinutes > 0) {
        println("Keeping IDE open for $keepOpenMinutes minute(s)")
        Thread.sleep(keepOpenMinutes * 60_000)
      }
      run.closeIdeAndWait()
    }

    if (System.getenv("KEEP_TEST_PROJECT").toBoolean()) {
      println("Keeping test project: $testProjectName")
    }
    else {
      deleteFlutterProject(testProjectName, directory = Paths.get(System.getProperty("user.home"), "IdeaProjects").toString())
    }
  }

  @Test
  fun newFlutterProject() {
    testProjectName = "my_flutter_app_${System.currentTimeMillis()}"
    println("Test project will be created as: $testProjectName")
    run = Setup.setupTestContextIU262(javaClass.simpleName).runIdeWithDriver()

    newProjectWelcomeScreen(run, testProjectName)
    waitForProjectReady()
    println("Project $testProjectName created and indexed.")
  }

  /**
   * Waits for the project frame and its background tasks. Right after opening, 2026.2 may rebuild frame components
   * (e.g. when the trial tab opens), which invalidates remote references, so retry with a fresh lookup.
   */
  private fun waitForProjectReady(attempts: Int = 3) {
    repeat(attempts) { attempt ->
      try {
        run.driver.withContext {
          ideFrame {
            waitFound()
            driver.waitForIndicators(5.minutes)
          }
        }
        return
      }
      catch (e: Exception) {
        if (attempt == attempts - 1) throw e
        println("Project frame not stable yet (${e.javaClass.simpleName}); retrying")
        Thread.sleep(5_000)
      }
    }
  }
}
