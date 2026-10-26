<!--* freshness: { reviewed: '2026-10-25' } *-->
# plugin

## Overview
The `plugin` Dart CLI (package `plugin_tool`). [`lib/plugin.dart`](lib/plugin.dart) `main` registers three commands: `lint`, `generate` (live templates), and `deploy` (dev channel upload). There are no build or test commands; the plugin is built and tested with Gradle. See [README.md](README.md) for usage.

Run it from the repository root with the wrapper [`../../bin/plugin`](../../bin/plugin), which calls `dart tool/plugin/bin/main.dart "$@"`.

## Interface & Configuration
- [`pubspec.yaml`](pubspec.yaml): Dart package specification. It uses `resolution: workspace`, so it is a pub workspace member of the root [`pubspec.yaml`](../../pubspec.yaml).
- [`CHANGELOG.md`](CHANGELOG.md): Change history for the plugin tool.
- [`README.md`](README.md): Tool documentation.
- [`plugin.iml`](plugin.iml): IntelliJ module file.

## Subdirectories & Boundaries
- bin: [./bin/ABOUT.md](./bin/ABOUT.md) - Entry point for the plugin tool.
- lib: [./lib/ABOUT.md](./lib/ABOUT.md) - Core library implementing the lint, generate, and deploy commands (plus unused legacy build code).
- test: Tests for the plugin tool ([`test/plugin_test.dart`](test/plugin_test.dart)). CI runs them in the `DART_BOT` job of [`../github.sh`](../github.sh).
