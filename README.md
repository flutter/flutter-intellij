# <img src="https://raw.githubusercontent.com/dart-lang/site-shared/refs/heads/main/src/_assets/image/flutter/icon/64.png" alt="Flutter" width="26" height="26"/> Flutter Plugin for IntelliJ

[![Latest plugin version](https://img.shields.io/jetbrains/plugin/v/9212)](https://plugins.jetbrains.com/plugin/9212-flutter)
[![Build Status](https://github.com/flutter/flutter-intellij/workflows/presubmit/badge.svg)](https://github.com/flutter/flutter-intellij/actions?query=branch%3Amain+workflow%3Apresubmit)

An IntelliJ plugin for [Flutter](https://flutter.dev/) development. Flutter is a multi-platform
app SDK to help developers and designers build modern apps for iOS, Android and the web.

## Documentation

- [flutter.dev](https://flutter.dev)
- [Installing Flutter](https://docs.flutter.dev/get-started/install)
- [Getting Started with IntelliJ](https://docs.flutter.dev/tools/android-studio)

## Fast development

Flutter's <em>hot reload</em> helps you quickly and easily experiment, build UIs, add features,
and fix bugs faster. Experience sub-second reload times, without losing state, on emulators,
simulators, and hardware for iOS and Android.

<img src="https://user-images.githubusercontent.com/919717/28131204-0f8c3cda-66ee-11e7-9428-6a0513eac75d.gif" alt="Make a change in your code, and your app is changed instantly.">

## Quick start

A brief summary of the [getting started guide](https://docs.flutter.dev/tools/android-studio):

- install the [Flutter SDK](https://docs.flutter.dev/get-started/install)
- run `flutter doctor` from the command line to verify your installation
- ensure you have a supported IntelliJ development environment; either:
    - the latest stable version of [IntelliJ](https://www.jetbrains.com/idea/download), Community or Ultimate Edition (EAP versions are not
      always supported)
    - the latest stable version of [Android Studio](https://developer.android.com/studio) (note: Android Studio Canary versions are
      generally _not_ supported)
- open the plugin preferences
    - `Preferences > Plugins` on macOS, `File > Settings > Plugins` on Linux, select "Browse repositories…"
- search for and install the 'Flutter' plugin
- choose the option to restart IntelliJ
- configure the Flutter SDK setting
    - `Preferences` on macOS, `File>Settings` on Linux, select `Languages & Frameworks > Flutter`, and set
      the path to the root of your flutter repo

## Filing issues

Please use our [issue tracker](https://github.com/flutter/flutter-intellij/issues)
for Flutter IntelliJ issues.

- for more general Flutter issues, you should prefer to use the Flutter
  [issue tracker](https://github.com/flutter/flutter/issues)
- for more Dart IntelliJ related issues, please use the Dart Plugin
  [issue tracker](https://github.com/flutter/dart-intellij-third-party/issues)

## Known issues

Please note the following known issues:

- [#601](https://github.com/flutter/flutter-intellij/issues/601): IntelliJ will
  read the PATH variable just once on startup. Thus, if you change PATH later to
  include the Flutter SDK path, this will not have an effect in IntelliJ until you
  restart the IDE.
- If you require network access to go through proxy settings, you will need to set the
  `https_proxy` variable in your environment as described in the
  [pub docs](https://dart.dev/tools/pub/troubleshoot#pub-get-fails-from-behind-a-corporate-firewall).
  (See also: [#2914](https://github.com/flutter/flutter-intellij/issues/2914).)

## Dev channel

If you like getting new features as soon as they've been added to the code then you
might want to try out the dev channel. It is updated daily with the latest contents
from the "main" branch. It has minimal testing. Set up instructions are in the wiki's
[dev channel page](./docs/Dev-Channel.md).

## Flutter SDK compatibility

These are the versions of Flutter SDK that current and previous Flutter plugins support:

| Flutter SDK version | Flutter plugin version |
|---------------------|------------------------|
| up to v3.7.12       | 83.0.4 and earlier     |
| v3.10.0 to v3.10.2  | 85.3.2 and earlier     |
| v3.10.3 to v3.10.6  | 86.0.2 and earlier     |
| v3.13.0 to v3.13.9  | 88.1.0 and earlier     |
| v3.16.0 and above   | Currently supported    |

Here is more information on the Flutter plugin's support for Flutter
SDKs: https://docs.flutter.dev/tools/sdk#sdk-support-for-flutter-developer-tools.

## AI Coding Agent Skills

This repository comes with custom configuration and automation skills for AI coding agents (such as Gemini Code Assist / Antigravity).

These skills are located in the [.agents/skills/](.agents/skills/) directory. They are automatically discovered and loaded by agentic workflows when they analyze the workspace.

### Available Workspace Skills:
* **[Add Missing Unit Test](.agents/skills/add-missing-unit-test/SKILL.md):** Add a new unit test for a class that currently lacks one or add a new test case to an existing test file, verifying improvement with Kover.
* **[Accessibility (A11y) Audit](.agents/skills/audit-accessibility/SKILL.md):** Ensure the plugin's custom UI components are accessible to users with screen readers and other assistive technologies.
* **[Dependency & Library Audit](.agents/skills/audit-dependencies/SKILL.md):** Optimize plugin size and security by removing unused dependencies and updating outdated libraries.
* **[UI Thread Safety Audit](.agents/skills/audit-ui-thread-safety/SKILL.md):** Prevent UI freezes and ensure a responsive user experience by validating threading rules and migrating blocking calls off the Event Dispatch Thread (EDT).
* **[Code Inspection Cleanup](.agents/skills/cleanup-code-inspections/SKILL.md):** Reduce technical debt and improve code quality by systematically resolving static analysis warnings.
* **[Unused Asset Cleanup](.agents/skills/cleanup-unused-assets/SKILL.md):** Reduce plugin size by scanning `resources/icons` and removing unreferenced assets.
* **[Code Review](.agents/skills/code-review/SKILL.md):** Performs a pedantic, multi-perspective code review (covering logic, correctness, resource safety, design, and styleguide compliance) on your uncommitted changes.
* **[Fix Specified Issue](.agents/skills/fix-specified-issue/SKILL.md):** Reproduce, test, fix, and verify a specific user-provided issue (stack trace or GitHub issue link) following plugin development guidelines.
* **[Documentation Coverage](.agents/skills/improve-documentation-coverage/SKILL.md):** Ensure public classes and methods in `src/` have Javadoc/KDoc to improve maintainability and API clarity.
* **[Single File Coverage Improvement](.agents/skills/improve-single-file-coverage/SKILL.md):** Write a new single test file, or modify an existing file, to improve coverage for a specific target class using Kover.
* **[Migrate IntelliJ Util](.agents/skills/migrate-intellij-util/SKILL.md):** Optimize memory usage, consistency, and performance by migrating standard Java/Kotlin classes to IntelliJ's specialized `com.intellij.util` implementations.
* **[Optimize Gradle Build](.agents/skills/optimize-gradle-build/SKILL.md):** Improve local build performance and CI efficiency by leveraging Gradle's caching and profiling tools.
* **[Prepare PR Cleanup](.agents/skills/prepare-pr-cleanup/SKILL.md):** Verify current test suite status with `./gradlew test`, clean up any temporary debug modifications, and harden test coverage for active files before opening a pull request.
* **[Resolve Verification Issues](.agents/skills/resolve-verification-issues/SKILL.md):** Eliminate plugin verification warnings and errors identified by `./gradlew verifyPlugin`.
* **[Update IntelliJ Platform Plugin](.agents/skills/update-intellij-platform-plugin/SKILL.md):** Update the `org.jetbrains.intellij.platform` Gradle plugin version to the latest available and run comprehensive validation checks.
* **[Verify EAP Compatibility](.agents/skills/verify-eap-compatibility/SKILL.md):** Ensure the plugin remains compatible with the latest IntelliJ Platform releases and EAP (Early Access Program) builds.

### How to use:
Tell your AI assistant to run the desired skill (e.g. by typing `/code-review` or asking *"Run the code-review skill on my changes"*). The agent will automatically find, load, and follow the instructions in the corresponding `SKILL.md` file.
