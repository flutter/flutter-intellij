<!--* freshness: { reviewed: '2026-08-31' } *-->
# Flutter SDK

## Overview
Manages the configuration, discovery, and path resolution for the Flutter SDK and Android SDK/Emulators.

## Interface
- `AbstractLibraryManager`
- `AndroidEmulatorManager`
- `FlutterCommand`
- `FlutterCommandStartResult`
- `FlutterCommandStartResultStatus`
- `FlutterCreateAdditionalSettings`
- `FlutterPluginLibraryProperties`
- `FlutterPluginLibraryType`
- `FlutterPluginsLibraryManager`
- `FlutterProjectActivity`
- `FlutterSdk`
- `FlutterSdkManager`
- `FlutterSdkUtil`
- `FlutterSdkVersion`
- `FlutterSearchableOptionContributor`
- `FlutterSettingsConfigurable`
- `XcodeUtils`

## Invariants
- AndroidEmulatorManager maintains a cached list of Android emulators, with a single instance per project.
- AbstractLibraryManager manages exactly one library root configured by its subclass.
- FlutterCommand encapsulates a flutter command execution, tracking the command type, target directory, and arguments.
- FlutterSdk manages a static cache of project SDK instances indexed by their canonical paths.
- FlutterSdkVersion parses and tracks SDK semantic versions, beta version tags, and capability thresholds.

## Side Effects
- Modifies module library dependencies and Project roots using IntelliJ's WriteAction and DumbService.
- Spawns background threads to fetch Android emulators using the Android SDK.
- Executes shell commands (e.g., flutter, 'open -a Simulator.app') as new OS processes using GeneralCommandLine.
- Sets ANDROID_HOME and FLUTTER_HOST environment variables for subprocesses.
- Modifies the DartPlugin's internal states and configures the Dart SDK paths.
