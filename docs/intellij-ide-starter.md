# IntelliJ IDE Starter Framework

This document provides an overview of the IntelliJ IDE Starter framework, which is used for writing UI-level integration tests for IntelliJ plugins.

## Overview

The [IntelliJ IDE Starter](https://github.com/JetBrains/intellij-ide-starter) is a framework developed by JetBrains for automating UI tests for IntelliJ-based IDEs. It allows you to programmatically start an IDE instance, install plugins, open projects, and interact with the UI.

The framework is built on top of the following key components:

*   **`com.intellij.ide.starter`**: The core module that provides the main entry points for creating and configuring test scenarios.
*   **`com.intellij.driver`**: The UI testing driver that allows you to find and interact with UI components.

## Core Concepts

### Test Case

A test case is represented by the `com.intellij.ide.starter.models.TestCase` class. It defines the IDE version and the project to be used for the test.

### IDE Test Context

The `com.intellij.ide.starter.ide.IDETestContext` is the main entry point for a test. It provides access to the IDE instance and allows you to perform actions such as installing plugins and interacting with the UI.

A new `IDETestContext` is created using the `Starter.newContext` method.

### Plugin Configurator

The `com.intellij.ide.starter.plugins.PluginConfigurator` is used to install plugins into the IDE instance. It can install plugins from a local path or from the JetBrains Plugin Repository.

### UI Interaction

The `com.intellij.driver` module provides the tools for interacting with the UI. The main entry point is the `driver` object, which is available within the `IDETestContext`.

The `driver` provides methods for finding UI components, such as `driver.find(By.text("OK"))`, and interacting with them, such as `component.click()`.

The framework also provides convenient access to common IDE components, such as `ideFrame`, `welcomeScreen`, and `projectView`.

## Writing a Test

Here is a basic example of a UI test using the IntelliJ IDE Starter framework:

```kotlin
import com.intellij.driver.sdk.ui.components.common.ideFrame
import com.intellij.ide.starter.ide.IDETestContext
import com.intellij.ide.starter.junit5.JUnit5StarterAssistant
import com.intellij.ide.starter.sdk.driver
import io.flutter.integrationTest.setupTestContextIC
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(JUnit5StarterAssistant::class)
class MyUITest {

    private lateinit var context: IDETestContext

    @Test
    fun testSomething() {
        context = setupTestContextIC("my-test")

        context.driver.sdk.let { driver ->
            // Your test logic here
            // For example, wait for the IDE frame to appear
            driver.ideFrame {
                // Interact with the IDE frame
            }
        }
    }
}
```

### Setup

The `setupTestContextIC` method in this example is a custom method defined in `testSrc/integration/io/flutter/integrationTest/Setup.kt`. It sets up the `IDETestContext` with the IntelliJ IDEA Community Edition and a specific version. It also installs the Flutter plugin and its dependencies.

### Test Execution

The `@ExtendWith(JUnit5StarterAssistant::class)` annotation is used to integrate the IntelliJ IDE Starter framework with JUnit 5. The `JUnit5StarterAssistant` takes care of starting and stopping the IDE instance for each test.

## Finding UI Components

The `com.intellij.driver` module provides a powerful mechanism for finding UI components. You can find components by various criteria, such as:

*   **Text**: `By.text("OK")`
*   **Accessible Name**: `By.accessibleName("Open Project")`
*   **Tooltip**: `By.tooltip("Show history")`
*   **Class Name**: `By.className("JButton")`
*   **XPath**: `By.xpath("//div[contains(@class, 'MyClass')]")`

Once a component is found, you can interact with it using methods like `click()`, `doubleClick()`, `typeText("some text")`, etc.

The framework also provides a `Finder` class that gives you more control over the search process.

## Further Reading

For more information, please refer to the official documentation on the [IntelliJ IDE Starter GitHub repository](https://github.com/JetBrains/intellij-ide-starter).
