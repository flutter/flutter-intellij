# Flutter and Dart IntelliJ Plugins

This repository is a monorepo containing both the **Flutter plugin** and the **Dart plugin** for IntelliJ IDEA and Android Studio.

## Repository Structure

- **[`flutter/`](flutter/)**: Flutter IntelliJ Plugin codebase, resources, documentation, and agent skills.
- **[`dart/`](dart/)**: Dart IntelliJ Plugin codebase, resources, documentation, and agent skills.

---

## Building and Testing

Both plugins are developed and built independently using their respective Gradle configurations.

### Flutter Plugin

For details on contributing, testing, and agent skills, see [`flutter/README.md`](flutter/README.md).

```bash
cd flutter

# Build the plugin zip archive
./gradlew buildPlugin

# Run unit tests
./gradlew test

# Verify plugin compatibility with target IDE builds
./gradlew verifyPlugin

# Launch a local IDE instance with the Flutter plugin loaded
./gradlew runIde
```

### Dart Plugin

For details on contributing, testing, and agent skills, see [`dart/README.md`](dart/README.md).

```bash
cd dart

# Build the plugin zip archive
./gradlew buildPlugin

# Run unit tests (from third_party directory)
cd third_party
./gradlew :test --tests "com.jetbrains.lang.dart.*"

# Verify plugin compatibility
./gradlew verifyPlugin

# Launch a local IDE instance with the Dart plugin loaded
./gradlew runIde
```
