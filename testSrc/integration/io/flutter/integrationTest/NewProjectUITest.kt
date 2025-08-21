/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.driver.sdk.ui.components.common.toolwindows.projectView
import com.intellij.driver.sdk.ui.components.common.welcomeScreen
import com.intellij.driver.sdk.wait
import com.intellij.driver.sdk.waitForIndicators
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.ide.starter.driver.engine.runIdeWithDriver
import com.intellij.ide.starter.junit5.config.UseLatestDownloadedIdeBuild
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Paths
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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

    newProjectWelcomeScreen()
    newProjectInProjectView()
  }

  @Test
  @Disabled("Need license configuration to test")
  fun newProjectUE() {
    println("Initializing IDE test context")
    println("Test project will be created as: $testProjectName")
    run = Setup.setupTestContextUE("MyProjectUITest").runIdeWithDriver()

    newProjectWelcomeScreen()
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

    newProjectWelcomeScreen()
    newProjectInProjectView()
  }


  /**
   * Automates the process of creating a new Flutter project from the welcome screen.
   * <p>
   * This function navigates the "New Project" dialog, selects the Flutter project type,
   * and enters a unique project name. It relies on the {@code FLUTTER_SDK} environment
   * variable to be set for the test to proceed.
   */
  fun newProjectWelcomeScreen() {
    run.driver.withContext {
      // Assert that the welcome screen is visible before interacting with it.
      welcomeScreen {
        assert(isVisible())
        println("Creating the new project from Welcome Screen")
        createNewProjectButton.click()

        // The test expects the `FlutterGeneratorPeer` to automatically populate the
        // Flutter SDK path. This behavior relies on the FLUTTER_SDK environment
        // variable being set.
        //
        // If the FLUTTER_SDK variable is not present, the UI will not find the SDK,
        // and the 'Next' button will remain disabled, causing the test to fail.
        // A common reason for this failure is an unconfigured test environment.

        newProjectDialog {
          // Wait for the dialog to fully load
          wait(1.seconds)

          // Select project type - adjust based on your needs
          chooseProjectType("Flutter")
          wait(1.seconds)

          // Expect setup to take care of setting the correct Flutter SDK
          if (!nextButton.isEnabled()) {
            fail { "The FLUTTER_SDK environment variable was not set." }
          }
          nextButton.click()
          wait(1.seconds)

          keyboard {
            typeText(testProjectName)
          }
          createButton.click()
        }
      }
    }
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

//
// TODO(jwren) move this into a utility for project creation tasks
//

// A Kotlin extension function for the `Finder` class.
// This function adds a new method, `newProjectDialog`, that can be
// called on any `Finder` object.
fun Finder.newProjectDialog(action: NewProjectDialogUI.() -> Unit) {
  // Locates the "New Project" dialog.
  // - `x(...)` creates an XPath-like query to find the UI component.
  // - The query targets a `div` with a `title` of "New Project".
  // - `NewProjectDialogUI::class.java` specifies that the found component
  //   should be treated as an instance of the `NewProjectDialogUI` class,
  //   allowing access to its properties and functions.
  //
  // The found dialog component is then passed to the `action` lambda,
  // which contains the test steps to perform within the dialog.
  x("//div[@title='New Project']", NewProjectDialogUI::class.java).action()
}

// A UI component representing the "New Project" dialog.
// This class provides a structured way to interact with the dialog's elements
// using the IntelliJ Driver SDK.
open class NewProjectDialogUI(data: ComponentData) : UiComponent(data) {

  // Clicks on the specified project type from the list.
  // The function waits until the text is found before performing the click action.
  fun chooseProjectType(projectType: String) {
    projectTypeList.waitOneText(projectType).click()
  }

  // Locates the list of project types within the dialog.
  // The xQuery targets a component with the specific class "JBList".
  private val projectTypeList = x("//div[@class='JBList']")

  // Locates the "Next" button in the dialog.
  // The xQuery finds a button component with the visible text "Next".
  val nextButton = x("//div[@text='Next']")

  // Locates the "Create" button in the dialog.
  // The xQuery finds a button component with the visible text "Create".
  val createButton = x("//div[@text='Create']")
}
