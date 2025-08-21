/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.toolwindows.projectView
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.junit5.config.UseLatestDownloadedIdeBuild
import io.flutter.integrationTest.utils.newProjectWelcomeScreen
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Paths
import kotlin.time.Duration.Companion.minutes

@Tag("ui")
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@ExtendWith(UseLatestDownloadedIdeBuild::class)
class MyProjectUITest {

  companion object {
    // Generate a unique folder name for the test project to avoid conflicts
    var testProjectName = ""

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
   * Empty for these tests as we want a different IDE. Setup is called in each test.
   */
  @BeforeEach
  fun initContext() {
    testProjectName = "my_test_project_${System.currentTimeMillis()}"
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
    println("Initializing IDE test context")
    println("Test project will be created as: $testProjectName")
    run = Setup.setupTestContextIC("MyProjectUITest").runIdeWithDriver()

    newProjectWelcomeScreen(run, testProjectName)
    newProjectInProjectView()
  }

  @Test
  @Disabled("Need license configuration to test")
  fun newProjectUE() {
    println("Initializing IDE test context")
    println("Test project will be created as: $testProjectName")
    run = Setup.setupTestContextUE("MyProjectUITest").runIdeWithDriver()

    newProjectWelcomeScreen(run, testProjectName)
    run.driver.withContext {
      ideFrame {
        // Wait for the ideFrame to appear before attempting to interact with it.
        // This is a positive assertion that confirms the UI transition is complete.
        waitFound()
      }
    }
  }

  @Test
  @Disabled("Need license configuration to test")
  fun newProjectWS() {
    println("Initializing IDE test context")
    println("Test project will be created as: $testProjectName")
    run = Setup.setupTestContextWS("MyProjectUITest").runIdeWithDriver()

    newProjectWelcomeScreen(run, testProjectName)
    newProjectInProjectView()
  }

  /**
   * Verifies the successful creation of a new project by asserting the presence
   * and structure of files in the project view.
   * <p>
   * This function is designed to be called after the project has been created. It waits
   * for the IDE to finish indexing and then confirms that all expected files and folders
   * are correctly displayed in the Project View tool window.
   */
  fun newProjectInProjectView() {
    /*
     * This function verifies the successful creation of a new project by asserting the
     * presence and structure of files in the project view.
     *
     * It is designed to be called after the project creation process has been initiated
     * and the main IDE window has opened.
     */
    run.driver.withContext {
      ideFrame {
        // Wait for the main IDE window to appear before attempting to interact with it.
        // This is a positive assertion that confirms the UI transition is complete.
        waitFound()

        // This is a crucial step that waits for the IDE to finish all background tasks,
        // such as indexing, downloading dependencies, and building the initial project.
        // This prevents race conditions where the test tries to interact with files
        // before they are fully loaded.
        driver.waitForIndicators(5.minutes)
        println("IDE is ready, accessing editor.")

        projectView {
          // Asserting the top-level files
          // Note: The `pathExists()` method is part of an older API.
          // The recommended method is `findPath().shouldExist()`.
          projectViewTree.pathExists(testProjectName, "README.md")
          projectViewTree.pathExists(testProjectName, "pubspec.yaml")

          // Asserting files inside specific directories
          projectViewTree.pathExists(testProjectName, "lib", "main.dart")
          projectViewTree.pathExists(testProjectName, "test", "widget_test.dart")

          // Asserting the existence of the main project folders
          projectViewTree.pathExists(testProjectName, ".dart_tool")
          projectViewTree.pathExists(testProjectName, "lib")
          projectViewTree.pathExists(testProjectName, "test")
          projectViewTree.pathExists(testProjectName, "android")
          projectViewTree.pathExists(testProjectName, "ios")
          projectViewTree.pathExists(testProjectName, "linux")
          projectViewTree.pathExists(testProjectName, "macos")
          projectViewTree.pathExists(testProjectName, "web")
          projectViewTree.pathExists(testProjectName, "windows")
          // Double-click the main file to open it in the editor.
          projectViewTree
            .waitFound()
            .doubleClickPath(testProjectName, "lib", "main.dart", fullMatch = false)
        }
      }
    }
  }
}
