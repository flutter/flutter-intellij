# Integration Tests

> **Note:** The tests in this directory are **experimental**.

## Current Status

- **No CI Integration:** These integration tests do not currently run in our Continuous Integration (CI) environment. `tool/github.sh` has an `INTEGRATION_BOT` branch, but the GitHub presubmit matrix does not include it.
- **Brittle & Environment-Dependent:** While these tests executed successfully on the creator's local setup when written, steps have not been taken to ensure reproducibility across different systems. Additionally, they are not routinely checked or maintained to confirm they still pass.
- **Manual Execution:** They can be run manually, but execution results may vary or fail depending on local configuration.

## Running

Run `./gradlew integration`. This directory is the `integration` source set in [`build.gradle.kts`](../../build.gradle.kts), and the tests use the [IntelliJ IDE Starter](https://github.com/JetBrains/intellij-ide-starter) framework with JUnit 5. The `integration` task runs them one at a time (`maxParallelForks = 1`) with a 1g–4g heap, against the plugin zip built by `buildPlugin`.

## Contents

All files are under `io/flutter/integrationTest/`:

- [`Setup.kt`](io/flutter/integrationTest/Setup.kt): Configures the IDE Starter test context: paths, plugin installation, VM options, and OS-specific settings.
- [`NewProjectUITest.kt`](io/flutter/integrationTest/NewProjectUITest.kt): UI test for the new Flutter project flow.
- [`DeepLinksToolWindowTest.kt`](io/flutter/integrationTest/DeepLinksToolWindowTest.kt): UI test for the Deep Links tool window.
- [`vmService/VmServiceTest.kt`](io/flutter/integrationTest/vmService/VmServiceTest.kt): Launches a `flutter test` process and exchanges VM Service messages with it over WebSocket.
- [`utils/NewProject.kt`](io/flutter/integrationTest/utils/NewProject.kt): Helpers for creating new projects in UI tests.

## Guidance & Strategy

- **Do Not Rely on These Tests:** Do not depend on these integration tests to verify plugin functionality or test for regressions.
- **Ongoing Evaluation:** We are currently evaluating:
  - The overall reliability and stability of the integration testing framework.
  - How effectively we can maintain these tests over time.
  - How best to incorporate integration testing into our overall plugin maintenance and testing strategy.
