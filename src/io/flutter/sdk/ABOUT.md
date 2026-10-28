<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter SDK

## Overview
Manages the configuration, discovery, and path resolution for the Flutter SDK and Android SDK/Emulators. Also hosts the plugin's settings UI (see below).

## Interface
- [`AbstractLibraryManager`](AbstractLibraryManager.java)
- [`AndroidEmulatorManager`](AndroidEmulatorManager.java)
- [`FlutterCommand`](FlutterCommand.java)
- [`FlutterCommandStartResult`](FlutterCommandStartResult.java)
- [`FlutterCommandStartResultStatus`](FlutterCommandStartResultStatus.java)
- [`FlutterCreateAdditionalSettings`](FlutterCreateAdditionalSettings.java)
- [`FlutterPluginLibraryProperties`](FlutterPluginLibraryProperties.java)
- [`FlutterPluginLibraryType`](FlutterPluginLibraryType.java)
- [`FlutterPluginsLibraryManager`](FlutterPluginsLibraryManager.java)
- [`FlutterProjectActivity`](FlutterProjectActivity.kt): Kotlin `ProjectActivity` that only calls `FlutterSdkManager.checkForFlutterSdkChange()` on project startup.
- [`FlutterSdk`](FlutterSdk.java)
- [`FlutterSdkManager`](FlutterSdkManager.java)
- [`FlutterSdkUtil`](FlutterSdkUtil.java)
- [`FlutterSdkVersion`](FlutterSdkVersion.java)
- [`FlutterSearchableOptionContributor`](FlutterSearchableOptionContributor.java): makes the Flutter settings page searchable.
- [`FlutterSettingsConfigurable`](FlutterSettingsConfigurable.java) (+ [`.form`](FlutterSettingsConfigurable.form)): the Flutter settings page; values are persisted through [`FlutterSettings`](../settings/FlutterSettings.java).
- [`XcodeUtils`](XcodeUtils.java)

## Invariants
- AndroidEmulatorManager maintains a cached list of Android emulators, with a single instance per project.
- AbstractLibraryManager manages a named project library (subclasses supply the default name, e.g. "Flutter Plugins") with any number of CLASSES root URLs. If the project does not declare Flutter, the library is removed instead.
- FlutterCommand encapsulates a flutter command execution, tracking the command type, target directory, and arguments.
- FlutterSdk manages a static cache of project SDK instances indexed by their canonical paths, and caches `flutter config` query results per instance (`cachedConfigValues`).
- FlutterSdkVersion reads `bin/cache/flutter.version.json` and tracks SDK semantic versions, beta version tags, and capability thresholds. `MIN_SDK_SUPPORTED` and `MIN_SDK_WITHOUT_SUNSET_WARNING` drive the outdated-SDK warnings; the other `MIN_SUPPORTS_*` constants gate features (DevTools multi-embed, property editor, widget preview).

## Side Effects
- Modifies module library dependencies and Project roots using IntelliJ's WriteAction; AbstractLibraryManager commits library root changes via `DumbService.runWhenSmart`.
- Spawns background threads to fetch Android emulators using the Android SDK.
- Executes shell commands (e.g., flutter, `xcode-select`, `open -a <simulator app>`) as new OS processes using GeneralCommandLine.
- XcodeUtils.openSimulator resolves the simulator app off the EDT via `xcode-select --print-path` (5s timeout), preferring Xcode 27+ `DeviceHub.app`, then `Developer/Applications/Simulator.app`, falling back to the bare `Simulator.app` name. `isSimulatorRunning` detects both `Simulator` and `DeviceHub` processes.
- FlutterCommand sets `FLUTTER_HOST` (appending the IDE's client ID to any existing value) and `ANDROID_HOME` (from the project's Android SDK, or the environment value when no project is given) for subprocesses. The device daemon sets `ANDROID_HOME` separately in [`run/daemon`](../run/daemon/ABOUT.md).
- FlutterSettingsConfigurable applies the SDK path inside a write action and performs some work (e.g. version lookups) on pooled threads.
- Modifies the DartPlugin's internal states and configures the Dart SDK paths.
