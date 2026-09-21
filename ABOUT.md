<!--* freshness: { reviewed: '2026-08-31' } *-->
# flutter-intellij Blueprint

## System Overview
The `flutter-intellij` repository provides the official Flutter plugin for IntelliJ IDEA and Android Studio. It integrates Flutter SDK capabilities, debugging, UI inspection, and project management directly into the IDE.

## Local Interface & Invariants
- `build.gradle.kts`, `settings.gradle.kts`: Gradle build configuration files for compiling the IntelliJ plugin.
- `pubspec.yaml`: Dart package configuration.

## Subdirectories & System Boundaries
- `docs`: Documentation for the plugin.
- `gradle`: Gradle wrapper configuration.
- `kokoro`: [./kokoro/ABOUT.md](./kokoro/ABOUT.md) - Configuration and scripts for the `flutter-intellij-kokoro` CI jobs on Google's internal Kokoro infrastructure.
- `releases`: Release scripts and assets.
- `resources`: UI layouts, messages, and standard static plugin resources.
- `src`: [./src/ABOUT.md](./src/ABOUT.md) - Root source directory containing the core Java and Kotlin implementations of the IDE plugin.
- `testData`: Test files and goldens for various IDE integration tests.
- `testSrc`: Source directory for IDE tests.
- `third_party`: [./third_party/ABOUT.md](./third_party/ABOUT.md) - Third-party libraries and Dart VM service drivers.
- `tool`: [./tool/ABOUT.md](./tool/ABOUT.md) - Root tooling directory for flutter-intellij. Contains grind tasks and other shell utilities for CI, deployment, and testing.

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
