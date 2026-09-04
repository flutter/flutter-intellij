# Flutter IntelliJ Plugin

This repository is a monorepo containing the IntelliJ IDEA and Android Studio plugins for Flutter.

## Repository Structure

- **[`flutter/`](flutter/)**: The Flutter IntelliJ Plugin codebase, documentation, and agent skills.

---

## Building and Testing

To develop, build, and test the Flutter plugin, navigate to the `flutter/` directory. For full setup, testing instructions, and AI agent skills, see [`flutter/README.md`](flutter/README.md).

```bash
cd flutter

# Build the plugin zip archive
./gradlew buildPlugin

# Run unit tests
./gradlew test

# Verify plugin compatibility with target IDE builds
./gradlew verifyPlugin

# Launch a local IDE instance with the plugin loaded
./gradlew runIde
```
