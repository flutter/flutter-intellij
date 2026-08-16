---
name: create-integration-test-from-issue
description: Creates a new integration test from a GitHub issue. Use when you want to create a failing integration test that reproduces a bug from a GitHub issue.
---

# Create Integration Test From Issue

## Overview

This skill creates a new integration test from a GitHub issue. The test is designed to reproduce the bug described in the issue and is expected to fail.

## Workflow

### Step 1: Get the GitHub issue content

The user will provide a URL to a GitHub issue. Use the `web_fetch` tool to get the content of the issue.

### Step 2: Create a new test file

Create a new test file in the `testSrc/integration/io/flutter/integrationTest` directory. The file name should be descriptive of the bug being tested. For example, if the issue is about a bug in the "new project" wizard, the file could be named `NewProjectWizardBugTest.kt`.

### Step 3: Write the test code

Use the information from the GitHub issue to write a new integration test. The test should reproduce the bug described in the issue.

The test should follow the structure of the existing integration tests in the project. Use the `docs/integration-testing.md` and `docs/intellij-ide-starter.md` files as a reference.

The test should be annotated with `@Test` and should be a method in a class that extends `JUnit5StarterAssistant`.

The test should be written in a way that it is expected to fail. For example, you can use `assert(false)` at the end of the test to ensure that it fails.

Here is an example of a failing test:

```kotlin
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.junit5.JUnit5StarterAssistant
import com.intellij.ide.starter.sdk.driver
import io.flutter.integrationTest.setupTestContextIC
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(JUnit5StarterAssistant::class)
class MyFailingUITest {

    private lateinit var context: IDETestContext

    @Test
    fun testBug() {
        context = setupTestContextIC("my-failing-test")

        context.driver.sdk.let { driver ->
            // Your test logic here
            // ...

            // This assertion will make the test fail
            assert(false) { "This test is expected to fail" }
        }
    }
}
```
