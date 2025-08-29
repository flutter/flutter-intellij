/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.integrationTest.utils

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.welcomeScreen
import com.intellij.driver.sdk.wait
import com.intellij.ide.starter.driver.engine.BackgroundRun
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
  private val projectTypeList = x("//div[@class='JBList']")

  // Locates the "Next" button in the dialog.
  // The xQuery finds a button component with the visible text "Next".
  val nextButton = x("//div[@text='Next']")

  // Locates the "Create" button in the dialog.
  // The xQuery finds a button component with the visible text "Create".
  val createButton = x("//div[@text='Create']")
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
