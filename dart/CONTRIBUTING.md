Contributing to Dart Plugin for IntelliJ
=======================

<!-- TOC -->
* [Contributing to Dart Plugin for IntelliJ](#contributing-to-dart-plugin-for-intellij)
  * [Contributing code](#contributing-code)
    * [Open Pull Request Limits](#open-pull-request-limits)
  * [Getting started](#getting-started)
  * [Environment set-up](#environment-set-up)
  * [IntelliJ set-up](#intellij-set-up)
    * [Open project and sync Gradle](#open-project-and-sync-gradle)
    * [Build and run the plugin](#build-and-run-the-plugin)
    * [Running on specific IDE versions or flavors](#running-on-specific-ide-versions-or-flavors)
  * [Building the plugin archive](#building-the-plugin-archive)
  * [Running plugin tests](#running-plugin-tests)
    * [Using the command line](#using-the-command-line)
    * [Using test run configurations in IntelliJ](#using-test-run-configurations-in-intellij)
  * [IntelliJ Plugin Verifier](#intellij-plugin-verifier)
    * [Updating verifier baselines](#updating-verifier-baselines)
  * [AI Coding Agent Skills](#ai-coding-agent-skills)
  * [Pull Request Checklist](#pull-request-checklist)
<!-- TOC -->

## Contributing code

![GitHub contributors](https://img.shields.io/github/contributors/flutter/dart-intellij-third-party.svg)

We gladly accept contributions via GitHub pull requests!
If you are new to coding IntelliJ plugins, here are a couple of links to get started:

- [INTRODUCTION TO CREATING INTELLIJ IDEA PLUGINS](https://developerlife.com/2020/11/21/idea-plugin-example-intro/)
- [ADVANCED GUIDE TO CREATING INTELLIJ IDEA PLUGINS](https://developerlife.com/2021/03/13/ij-idea-plugin-advanced/)

You must complete the [Contributor License Agreement](https://cla.developers.google.com/clas)
before any of your contributions with code get merged into the repo.

### Open Pull Request Limits

To ensure our maintainers can provide timely and high-quality feedback, public Flutter repositories limit open pull requests to 2 concurrent open pull requests for contributors without write access.

* **Draft PRs are exempt:** Work-in-progress draft PRs do not count toward your limit.
* **Focus on Quality:** Once you reach the limit, please focus on merging or closing your existing PRs before opening new ones.

## Getting started

1. Install the Dart SDK from [Dart SDK download](https://dart.dev/get-dart) or the Flutter SDK from [Flutter SDK download](https://flutter.dev/docs/get-started/install) (which includes the Dart SDK).
2. Fork `https://github.com/flutter/dart-intellij-third-party` into your own GitHub account.
   If you already have a fork and are now installing a development environment on a new machine,
   make sure you've updated your fork with the `main` branch
   so that you don't use stale configuration options from long ago.
3. Clone your fork:
   ```shell
   git clone https://github.com/<your_name_here>/dart-intellij-third-party
   ```
4. `cd dart-intellij-third-party`
5. `git remote add upstream https://github.com/flutter/dart-intellij-third-party`
   The name `upstream` can be whatever you want.

> [!NOTE]
> The repository includes top-level `gradlew` and `gradlew.bat` scripts that delegate directly to the `third_party` subproject directory. Commands can be run either from the repository root (e.g., `./gradlew <task>`) or from inside the `third_party` directory.

## Environment set-up

1. Install Java Development Kit 21 (JDK 21).
    - **[Googlers only]** Install Java from go/softwarecenter instead.

2. Set your `JAVA_HOME` directory in the configuration file for your shell environment.
    - For example, on macOS:
      Check what version of Java you have:
      ```shell
      /usr/libexec/java_home -V
      ```
      In your shell configuration file (e.g. `.bashrc` or `.zshrc`), set your `JAVA_HOME` env variable:
      ```shell
      export JAVA_HOME=`/usr/libexec/java_home -v 21`
      ```

3. Set your `DART_SDK` / `DART_HOME` path in the configuration file for your shell environment.
    - If using a standalone Dart SDK:
      ```shell
      export DART_SDK="/path/to/dart-sdk"
      export DART_HOME="$DART_SDK"
      ```
    - If using the Dart SDK embedded in the Flutter SDK:
      ```shell
      export FLUTTER_SDK="$HOME/path/to/flutter"
      export DART_SDK="$FLUTTER_SDK/bin/cache/dart-sdk"
      export DART_HOME="$DART_SDK"
      ```
    > [!IMPORTANT]
    > `DART_HOME` (or `DART_SDK`) must point to a valid Dart SDK root directory containing the `version` file so that Analysis Server test suites can locate the SDK binaries.

4. Add `DART_SDK` and `JAVA_HOME` to your `PATH`:
    ```shell
    export PATH=$DART_SDK/bin:$JAVA_HOME/bin:$PATH
    ```

5. Update your current `PATH`:
    - Either restart your terminal or run `source ~/.zshrc` / `source ~/.bashrc` to add the new environment variables to your `PATH`.

## IntelliJ set-up

1. Make sure you're using the latest stable release of IntelliJ IDEA,
   or download and install [IntelliJ IDEA Ultimate](https://www.jetbrains.com/idea/buy) or [IntelliJ IDEA Community](https://www.jetbrains.com/idea/download).

### Open project and sync Gradle

2. Start IntelliJ IDEA and open the project:
   - From the "Welcome to IntelliJ IDEA" dialog, select **Open** and choose either the repository root directory or the `third_party` directory in this repository (opening `third_party` opens the main Gradle root module directly).
   - If you see a popup with "Gradle build scripts found", **confirm loading the Gradle project, and wait until syncing is done.**

### Build and run the plugin

3. Launch a sandboxed IDE instance with the Dart plugin loaded:
   - From IntelliJ: Open **View > Tool Windows > Gradle**, expand tasks, and double-click **intellij > runIde**.
   - Or from the terminal (run from root or `third_party`):
     ```shell
     ./gradlew runIde
     ```

### Running on specific IDE versions or flavors

You can test the plugin against specific IDE distributions (IntelliJ Community, Ultimate, or Android Studio) and specific versions using the `runTarget` task:

- **Run in IntelliJ IDEA Community (specific version):**
  ```shell
  ./gradlew runTarget -Pide=IntelliJ -PideV=2025.1
  ```
- **Run in IntelliJ IDEA Ultimate:**
  ```shell
  ./gradlew runTarget -Pide=Ultimate -PideV=2025.1
  ```
- **Run against a local IDE installation (e.g. Android Studio):**
  ```shell
  ./gradlew runTarget -PidePath="/Applications/Android Studio.app"
  ```
- **List available IDE product releases:**
  ```shell
  ./gradlew printProductsReleases
  ```

## Building the plugin archive

To package the plugin into a deployable `.zip` archive:

```shell
./gradlew buildPlugin
```

The output ZIP file will be placed in `third_party/build/distributions/`.

## Running plugin tests

The test suite is split between unit tests under `src/main/test/java/com/jetbrains/lang/dart` and Dart Analysis Server tests under `src/main/test/java/com/jetbrains/dart/analysisServer`.

### Using the command line

Run all tests:
```shell
./gradlew test
```

Run **unit tests**:
```shell
./gradlew test --tests "com.jetbrains.lang.dart.*"
```

Run **Dart Analysis Server tests** (requires `DART_HOME` or `DART_SDK` to be set):
```shell
./gradlew test --tests "com.jetbrains.dart.analysisServer.*"
```

### Using test run configurations in IntelliJ

- You can run or debug individual tests or test packages directly within IntelliJ IDEA by right-clicking test classes/methods or setting up Gradle test run configurations.

## IntelliJ Plugin Verifier

The project uses the [IntelliJ Plugin Verifier](https://github.com/JetBrains/intellij-plugin-verifier) to check binary compatibility against specified IntelliJ Platform builds.

To run the verifier locally:
```shell
./gradlew verifyPlugin
```

### Updating verifier baselines

If new verification issues are found that match expected platform updates, update the baseline files.

> [!IMPORTANT]
> The baseline update scripts **must be executed from the root directory of the repository**.

- **Linux / macOS:**
  ```shell
  ./third_party/tool/update_baselines.sh
  ```

- **Windows:**
  ```cmd
  third_party\tool\update_baselines.bat
  ```

## AI Coding Agent Skills

This repository includes custom configuration and automation skills for AI coding agents (such as Gemini Code Assist / Antigravity) located in `.agents/skills/`:

* **[Code Review](.agents/skills/code-review/SKILL.md):** Performs a pedantic, multi-perspective code review on your uncommitted changes.
* **[Migrate DAS to LSP](.agents/skills/migrate-das-to-lsp/SKILL.md):** Guide for converting legacy Dart Analysis Server (DAS) feature implementations to JetBrains LSP.
* **[Monthly Release](.agents/skills/monthly-release/SKILL.md):** Step-by-step guide for preparing, validating, testing, and publishing monthly releases of the Dart plugin.
* **[Patch Copied LSP Sources](.agents/skills/patch-copied-lsp-sources/SKILL.md):** Automates copying and patching of JetBrains LSP sources.
* **[Port PR](.agents/skills/port-pr/SKILL.md):** Fetches a Pull Request from either `dart-intellij-third-party` or `flutter-intellij` and conceptually ports its changes to the other repository.

If you add or update any agent skills, run the documentation checker to verify that all skills remain documented in `README.md`:
```shell
./tool/check_agent_skills.sh
```

## Pull Request Checklist

Before submitting a Pull Request, please ensure:

1. [ ] You have signed the [Google CLA](https://cla.developers.google.com/clas).
2. [ ] Your code compiles cleanly with JDK 21.
3. [ ] All unit tests pass: `./gradlew test`.
4. [ ] Plugin verification passes: `./gradlew verifyPlugin`.
5. [ ] If adding/modifying AI agent skills, `./tool/check_agent_skills.sh` passes.
6. [ ] Opening this pull request will not exceed the limit of 2 concurrent open non-draft pull requests.
