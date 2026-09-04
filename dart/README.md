# Dart IntelliJ Plugin

![Latest Plugin Version](https://img.shields.io/jetbrains/plugin/v/Dart)
![Dev Version](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fplugins.jetbrains.com%2Fapi%2Fplugins%2F6351%2Fupdates%3Fchannel%3Ddev%26size%3D1&query=%24%5B0%5D.version&label=dev%20release&color=lightgrey)

[![presubmit](https://github.com/flutter/dart-intellij-third-party/actions/workflows/presubmit.yaml/badge.svg)](https://github.com/flutter/dart-intellij-third-party/actions/workflows/presubmit.yaml)
[![integration tests](https://github.com/flutter/dart-intellij-third-party/actions/workflows/integration_tests.yaml/badge.svg?label=integration%20tests)](https://github.com/flutter/dart-intellij-third-party/actions/workflows/integration_tests.yaml)


## Installing Dev Builds

To receive development builds of the Dart plugin in IntelliJ IDEA:

1. Open **Settings** (or **Preferences** on macOS) in IntelliJ IDEA.
2. Go to **Plugins**.
3. Click the ⚙️ (**Gear icon**) at the top of the Plugins panel and select **Manage Plugin Repositories...**.
4. Click **+** and add the dev channel repository URL:
   ```text
   https://plugins.jetbrains.com/plugins/dev/6351
   ```
5. Click **OK**. The IDE will now check for and install updates from the `dev` channel.


## Development setup

1. Clone this repository with

    ```shell
    git clone git@github.com:flutter/dart-intellij-third-party.git
    ```
   
2. Download the latest stable [IntelliJ Ultimate](https://www.jetbrains.com/idea/buy) or [IntelliJ Community](https://www.jetbrains.com/idea/download).
3. From the "Welcome to IntelliJ IDEA" dialog, select "Open" and then select `third_party` directory in this repository.
4. View > Tool Windows > Gradle, and click the button "Sync All Gradle Projects".
5. The Java source code should now be compiled. To launch an IDE with this plugin, execute the following on the command line (from the `third_party` directory):

    ```shell
   ./gradlew runIde
    ```

### Gradle tasks

This project is built with the [IntelliJ Platform Gradle Plugin](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html)

To see all current and valid Gradle tasks execute
```shell
./gradlew tasks
```

A subset of the current output from `tasks` command:
```
----------------------------------------------------------
Tasks runnable from root project 'Dart'
------------------------------------------------------------

Build tasks
-----------
assemble - Assembles the outputs of this project.
build - Assembles and tests this project.
...

IDE tasks
---------
cleanIdea - Cleans IDEA project files (IML, IPR)
idea - Generates IDEA project files (IML, IPR, IWS)
openIdea - Opens the IDEA project

Intellij platform tasks
-----------------------
buildPlugin - Builds the plugin and prepares the ZIP archive for testing and deployment.
runIde - Runs the IDE instance using the currently selected IntelliJ Platform with the built plugin loaded.
verifyPlugin - Runs the IntelliJ Plugin Verifier CLI tool to check the binary compatibility with specified IDE builds.
...
```

### Tests

To run **all** tests execute
```shell
./gradlew test
```

However, in the project tests are split between the unit tests under
`src/main/test/java/com/jetbrains/lang/dart` and the Dart Analysis Server tests under
`src/main/test/java/com/jetbrains/dart/analysisServer`.

To run the **unit tests** on the command line run:
```shell
./gradlew test --tests "com.jetbrains.lang.dart.*"
```

To run the **Dart Analysis Server tests**, first set a `DART_HOME` (configured and set in
`.github/workflows/presubmit.yaml` and in the tests themselves), then on the command line run:
```shell
./gradlew test --tests "com.jetbrains.dart.analysisServer.*"
```

These test suites can be configured as Gradle run configurations for running and debugging the tests from the IDE.

Each of these test suites are run on GitHub presubmits, configured in `.github/workflows/presubmit.yaml`.

### IntelliJ Plugin Verifier

See `intellij-plugin-verifier`, https://github.com/JetBrains/intellij-plugin-verifier.

To run the verifier run:
```shell
./gradlew verifyPlugin
```

The plugin verification is run on GitHub presubmits, configured in `.github/workflows/presubmit.yaml`.

## Project and repository history

This repository was originally a copy of the [Dart Plugin for IntelliJ from JetBrains](https://github.com/JetBrains/intellij-plugins/tree/master/Dart) through the commit https://github.com/JetBrains/intellij-plugins/commit/c912b13b1dd8acccd6259dedc4063848a8f87b2b.

Before the move to this repository, the project had 4 working and active branches which were used to build different IntelliJ versions:
- `243` - 2024.3
- `251` - 2025.1
- `252` - 2025.2
- `master` - the current master for intellij-community, targeting the next version 2025.3

In its original location the project was built with Bazel; however, in github.com/flutter, the project is built using Gradle (see https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html).

## AI Coding Agent Skills

This repository comes with custom configuration and automation skills for AI coding agents (such as Gemini Code Assist / Antigravity).

These skills are located in the [.agents/skills/](.agents/skills/) directory. They are automatically discovered and loaded by agentic workflows when they analyze the workspace.

### Available Workspace Skills:
* **[Code Review](.agents/skills/code-review/SKILL.md):** Performs a pedantic, multi-perspective code review (covering logic, correctness, resource safety, design, and styleguide compliance) on your uncommitted changes.
* **[Implement Dart Language Feature](.agents/skills/implement-dart-language-feature/SKILL.md):** Guidelines for implementing and reviewing new Dart language features, parsing logic, and grammar modifications.
* **[Migrate DAS to LSP](.agents/skills/migrate-das-to-lsp/SKILL.md):** Guide for converting legacy Dart Analysis Server (DAS) feature implementations to JetBrains LSP in the Dart IntelliJ plugin.
* **[Monthly Release](.agents/skills/monthly-release/SKILL.md):** Step-by-step guide for preparing, validating, testing, and publishing monthly releases of the Dart IntelliJ plugin.
* **[Patch Copied LSP Sources](.agents/skills/patch-copied-lsp-sources/SKILL.md):** Automates copying and patching of JetBrains LSP sources.
* **[Plugin Issue Triage](.agents/skills/plugin-issue-triage/SKILL.md):** (Experimental) Automates the triage of GitHub issues in the Dart and Flutter IntelliJ plugins.
* **[Port PR](.agents/skills/port-pr/SKILL.md):** Fetches a Pull Request from either the dart-intellij-third-party or flutter-intellij repository and conceptually ports its changes to the other repository.
* **[Root Cause Regression](.agents/skills/root-cause-regression/SKILL.md):** Investigates a GitHub issue reporting a regression, analyzes recent commits, and identifies possible culprits.

### How to use:
Tell your AI assistant to run the desired skill (e.g. by typing `/code-review` or asking *"Run the code-review skill on my changes"*). The agent will automatically find, load, and follow the instructions in the corresponding `SKILL.md` file.

## Contributing to this plugin

If you would like to contribute to the Dart plugin, see our [contribution guide](CONTRIBUTING.md).

