/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest.utils

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.welcomeScreen
import com.intellij.driver.sdk.wait
import com.intellij.ide.starter.driver.engine.BackgroundRun
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import org.junit.jupiter.api.fail
import kotlin.time.Duration.Companion.seconds

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
  internal val projectTypeList = x("//div[@class='JBList']")

  // Locates the "Next" button in the dialog.
  // The xQuery finds a button component with the visible text "Next".
  val nextButton = x("//div[@text='Next']")

  // Locates the "Create" button in the dialog.
  // The xQuery finds a button component with the visible text "Create".
  val createButton = x("//div[@text='Create']")
  val flutterSdkPath = x("//div[./div/text()='Flutter SDK:']//div[@class='TextFieldWithBrowseButton']")
}

/**
 * Automates the process of creating a new Flutter project from the welcome screen.
 * <p>
 * This function navigates the "New Project" dialog, selects the Flutter project type,
 * and enters a unique project name. It relies on the {@code FLUTTER_SDK} environment
 * variable to be set for the test to proceed.
 */
fun newProjectWelcomeScreen(run: BackgroundRun, testProjectName: String) {
  run.driver.withContext {
    // The test operates within the context of the IDE's welcome screen.
    welcomeScreen {
      // STEP 1: Start the "New Project" wizard.
      println("Creating the new project from Welcome Screen")
      createNewProjectButton.click()

      // This block interacts with the "New Project" dialog.
      newProjectDialog {
        // STEP 2: Select "Flutter" as the project type.
        // We wait for the list of project types to appear before making a selection.
        projectTypeList.waitFound()
        chooseProjectType("Flutter")

        // STEP 3: Navigate to the next screen.
        // The "Next" button will only be enabled if the Flutter SDK is correctly
        // configured in the test environment. We wait up to 15 seconds for it
        // to become enabled before proceeding.
        nextButton.waitFound(15.seconds)
        if (!nextButton.isEnabled()) {
          fail { "The 'Next' button is disabled, which likely means the Flutter SDK path is not set. Please make sure the FLUTTER_SDK environment variable is set." }
        }
        nextButton.click()

        // STEP 4: Enter project details and create the project.
        // We wait for the "Create" button to appear on the second screen of the
        // wizard before typing the project name and clicking the button.
        createButton.waitFound()

        keyboard {
          typeText(testProjectName)
        }
        createButton.doubleClick()
      }
    }
  }
}
