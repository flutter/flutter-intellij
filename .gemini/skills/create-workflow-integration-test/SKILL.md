---
name: create-workflow-integration-test
description: Interactively creates a new integration test for a specific workflow in the IntelliJ workspace. Use when you want to create a failing integration test for a user-described workflow.
---

# Create Workflow Integration Test

## Overview

This skill interactively creates a new integration test for a specific workflow in the IntelliJ workspace. The user will describe the workflow and the steps to follow, and the skill will generate a failing integration test that follows those steps.

## Workflow

### Step 1: Get the workflow description

Ask the user to describe the workflow they want to test. This will be used to generate a descriptive name for the test file.

### Step 2: Get the workflow steps

Ask the user to provide the steps of the workflow as a numbered list. These steps will be used to generate the test code.

For example:
1. Open the 'New Project' wizard.
2. Select 'Flutter'.
3. Set the project name to 'my_test_app'.
4. Click 'Finish'.
5. Verify that the project is created and the `main.dart` file is opened.

### Step 3: Create a new test file

Create a new test file in the `testSrc/integration/io/flutter/integrationTest` directory. The file name should be descriptive of the workflow being tested. For example, if the user wants to test the "new project" workflow, the file could be named `NewProjectWorkflowTest.kt`.

### Step 4: Write the test code

Use the information from the user's steps to write a new integration test. The test should reproduce the workflow described by the user.

The test should follow the structure of the existing integration tests in the project. Use the `docs/integration-testing.md` and `docs/intellij-ide-starter.md` files as a reference.

The test should be annotated with `@Test` and should be a method in a class that extends `JUnit5StarterAssistant`.

The test should be written in a way that it is expected to fail. You can use `assert(false)` at the end of the test to ensure that it fails.

Here is an example of a failing test for the "new project" workflow described above. Note that the comments should be replaced with the actual UI interactions.

```kotlin
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.junit5.JUnit5StarterAssistant
import com.intellij.ide.starter.sdk.driver
import io.flutter.integrationTest.setupTestContextIC
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(JUnit5StarterAssistant::class)
class NewProjectWorkflowTest {

    private lateinit var context: IDETestContext

    @Test
    fun testNewProjectWorkflow() {
        context = setupTestContextIC("new-project-workflow-test")

        context.driver.sdk.let { driver ->
            // 1. Open the 'New Project' wizard.
            // Implement UI interaction to open the new project wizard

            // 2. Select 'Flutter'.
            // Implement UI interaction to select 'Flutter'

            // 3. Set the project name to 'my_test_app'.
            // Implement UI interaction to set the project name

            // 4. Click 'Finish'.
            // Implement UI interaction to click 'Finish'

            // 5. Verify that the project is created and the `main.dart` file is opened.
            // Implement UI interaction to verify the project creation

            // This assertion will make the test fail
            assert(false) { "This test is expected to fail" }
        }
    }
}
```
