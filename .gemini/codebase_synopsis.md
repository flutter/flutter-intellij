# Codebase Synopsis: flutter-intellij

This document provides a high-level overview of the `flutter-intellij` plugin codebase to guide future development and maintenance.

## Summary of Findings

The flutter-intellij plugin is a complex IntelliJ Platform plugin with a large number of extensions and actions. The core of the plugin's functionality is defined in `plugin.xml`, with IDE-specific features, particularly for Android Studio, being loaded from separate configuration files like `studio-contribs.xml`. The plugin is deeply integrated with the IDE, providing custom tool windows, run configurations, editor features, and more.

Key areas for investigation for any task are:
- The startup activities (`FlutterInitializer`)
- Project services (`FlutterSdkManager`, `DeviceService`)
- The implementation of various actions and run configurations.

The `RelevantLocations` listed below are a good starting point for a deeper dive into the code.

## Exploration Trace

- Used `list_directory` to explore the project structure.
- Used `glob` to find `plugin.xml`.
- Read `plugin.xml` to understand the plugin's main components.
- Used `glob` to find the `*-contribs.xml` files.
- Read `android-contribs.xml`, `idea-contribs.xml`, `studio-contribs.xml`, and `gemini-contribs.xml`.

## Relevant Locations

| File Path | Reasoning | Key Symbols |
|---|---|---|
| `C:\work\flutter-intellijesources\META-INF\plugin.xml` | This is the main descriptor file for the plugin. It defines all the actions, extensions, and extension points that the plugin uses. Understanding this file is crucial to understanding the plugin's architecture. | `<actions>`, `<extensions>`, `<extensionPoints>` |
| `C:\work\flutter-intellijesources\META-INF\studio-contribs.xml` | This file defines the Android Studio-specific contributions of the plugin. It shows how the plugin adapts its functionality based on the IDE it's running in. | `<extensions>`, `<actions>` |
| `C:\work\flutter-intellij\src\io\flutter\FlutterInitializer.java` | This class is registered as a `postStartupActivity` and is likely one of the main entry points for the plugin's initialization logic. | `FlutterInitializer` |
| `C:\work\flutter-intellij\src\io\flutter\sdk\FlutterSdkManager.java` | This class is registered as a `projectService` and is likely responsible for managing the Flutter SDK configuration for a project. | `FlutterSdkManager` |
