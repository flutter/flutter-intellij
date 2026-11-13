# Integration Testing

This document outlines the process for running and writing integration tests for the Flutter IntelliJ plugin.

## Overview

Our integration tests are built using the [IntelliJ IDE Starter](./intellij-ide-starter.md) framework. This framework allows us to programmatically start a new instance of IntelliJ IDEA, install the Flutter plugin, and then drive the UI for testing purposes.

The tests are written in Kotlin and use JUnit 5 as the test runner.

## Location

Integration tests are located in the `testSrc/integration` directory.

## Running Tests

To run all integration tests, execute the following command from the root of the project:

```bash
./gradlew integration
```

## Configuration

The main configuration for integration tests can be found in a few key files:

*   **`build.gradle.kts`**: This file defines a custom Gradle source set named `integration`. It also configures the `integration` task to run the tests in this source set.
*   **`testSrc/integration/io/flutter/integrationTest/Setup.kt`**: This file contains the core logic for setting up the test environment. It uses the IntelliJ IDE Starter framework to configure a test instance of IntelliJ, install the Flutter plugin and its dependencies, and set any necessary JVM options.

## Example Test

A good example to start with is `testSrc/integration/io/flutter/integrationTest/NewProjectUITest.kt`. This test demonstrates how to:
1.  Launch the IDE with the Flutter plugin installed.
2.  Create a new Flutter project.
3.  Interact with the UI to verify that the project was created correctly.

Tests are annotated with `@Tag("ui")` to distinguish them as UI tests.
