/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.junit5.config.UseLatestDownloadedIdeBuild
import io.flutter.integrationTest.utils.newProjectWelcomeScreen
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Paths

@Tag("ui")
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@ExtendWith(UseLatestDownloadedIdeBuild::class)
class PreferencePagesUITest {

  companion object {
    // Generate a unique folder name for the test project to avoid conflicts
    var testProjectName = "my_test_project_${System.currentTimeMillis()}"

    /**
     * Cleanup method that runs after all tests in this class.
     * Removes the test project folder created during testing to keep the system clean.
     */
    @JvmStatic
    @AfterAll
    fun cleanUpTestFolder() {
      val projectPath = Paths.get(System.getProperty("user.home"), "IdeaProjects", testProjectName)
      val projectFile = projectPath.toFile()
      if (projectFile.exists()) {
        projectFile.deleteRecursively()
        println("Successfully deleted test folder: $projectPath")
      } else {
        println("Test folder does not exist, skipping cleanup: $projectPath")
      }
    }
  }

  /**
   * The IDE instance running in the background.
   * Initialized in @BeforeEach and closed in @AfterEach.
   */
  private lateinit var run: BackgroundRun

  /**
   * TODO document
   */
  @BeforeEach
  fun initContext() {
    println("Initializing IDE test context")
    println("Test project will be created as: $testProjectName")
    // TODO consider creating from github as a source instead: GitHubProject.fromGithub()
    run = Setup.setupTestContextIC("MyProjectUITest").runIdeWithDriver()
    newProjectWelcomeScreen(run, testProjectName)
  }

  /**
   * Tears down the test environment after each test method.
   *
   * Safely closes the IDE instance if it was successfully started.
   * The initialization check prevents errors if the setup failed.
   */
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
  fun newProjectIC() {
    // TODO work in progress here
  }
}

