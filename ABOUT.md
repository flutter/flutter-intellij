<!--* freshness: { reviewed: '2026-10-25' } *-->
# flutter-intellij Blueprint

## System Overview
The `flutter-intellij` repository provides the official Flutter plugin for IntelliJ IDEA and Android Studio. It integrates Flutter SDK capabilities, debugging, UI inspection, and project management directly into the IDE.

## Local Interface & Invariants
- [`build.gradle.kts`](build.gradle.kts), [`settings.gradle.kts`](settings.gradle.kts): Gradle build configuration files for compiling, testing, and verifying the IntelliJ plugin.
- [`gradle.properties`](gradle.properties): Build properties: target Android Studio version, Dart plugin version, `sinceBuild`, and Java version.
- [`pubspec.yaml`](pubspec.yaml): Root Dart workspace (`workspace: - tool/plugin`). Its dev dependencies (`grinder`, `skills_lint`, etc.) support the Dart tooling in [`tool/`](tool/), e.g. [`tool/grind.dart`](tool/grind.dart).
- [`analysis_options.yaml`](analysis_options.yaml): Dart analyzer and lint configuration for the repository's Dart tooling.
- [`bin/plugin`](bin/plugin): Wrapper script that runs `dart tool/plugin/bin/main.dart` (the plugin build/release tool).
- [`CHANGELOG.md`](CHANGELOG.md): Per-release user-facing change notes.
- [`CLAUDE.md`](CLAUDE.md): Claude Code guide. It imports [`.gemini/styleguide.md`](.gemini/styleguide.md) and adds a few rules.

## Subdirectories & System Boundaries
- `.agents`: [./.agents/AGENTS.md](./.agents/AGENTS.md) - Cross-agent skills (`skills/`), rules (`rules/`), and design docs (`design-docs/`).
- `.gemini`: [./.gemini/README.md](./.gemini/README.md) - Gemini Code Assist configuration and style guide.
- `.github`: GitHub configuration. [`.github/workflows/`](.github/workflows/) holds the `presubmit`, `check_urls`, `update_flutter`, and `no-response` workflows.
- `docs`: [./docs/README.md](./docs/README.md) - Development and process documentation for the plugin.
- `gradle`: Gradle wrapper configuration.
- `kokoro`: [./kokoro/ABOUT.md](./kokoro/ABOUT.md) - Configuration and scripts for the `flutter-intellij-kokoro` CI jobs on Google's internal Kokoro infrastructure.
- `resources`: UI layouts, messages, and standard static plugin resources. See also [./resources/flutter/README.md](./resources/flutter/README.md).
- `src`: [./src/ABOUT.md](./src/ABOUT.md) - Root source directory containing the core Java and Kotlin implementations of the IDE plugin.
- `testData`: Test files and goldens for various IDE integration tests.
- `testSrc`: Source directory for IDE tests. Roots: `unit/` (unit tests run by `./gradlew test`), `integration/` (experimental UI tests, see [./testSrc/integration/README.md](./testSrc/integration/README.md)), plus `com/` and `io/`.
- `third_party`: [./third_party/ABOUT.md](./third_party/ABOUT.md) - Third-party libraries and IntelliJ plugin dependencies.
- `tool`: [./tool/ABOUT.md](./tool/ABOUT.md) - Root tooling directory for flutter-intellij. Contains grind tasks and other shell utilities for CI, deployment, and testing.

A local `releases/` directory may appear as untracked build output. It is not part of the source tree.

## Global Invariants
- All plugin UI interactions must execute on the Event Dispatch Thread (EDT) unless explicitly marked for background execution.
- Network and VM Service interactions must be performed asynchronously to avoid freezing the IDE.

## Global Side Effects
- Mutates the local filesystem to create and manage Flutter projects.
- Spawns external processes (`flutter`, `dart`, `gradlew`) to handle compilation, testing, and execution.
- Establishes persistent WebSocket connections to running Dart VM instances for debugging and hot reload.

## System Verification
- Run: `./gradlew test` to execute unit tests.
- Run: `./gradlew buildPlugin` to verify compilation.
- Run: `./gradlew verifyPlugin` to run the IntelliJ Plugin Verifier (uses `third_party/lib/verifier-cli-1.398-all.jar`).
- Run: `./gradlew integration` to run the experimental integration tests (not run in CI).
