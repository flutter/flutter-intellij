<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter Settings

## Overview
Manages persistent IDE settings and preferences for the Flutter plugin. The settings UI that edits these values lives in `src/io/flutter/sdk/`: [`FlutterSettingsConfigurable`](../sdk/FlutterSettingsConfigurable.java) and [`FlutterSearchableOptionContributor`](../sdk/FlutterSearchableOptionContributor.java).

## Interface
- `class FlutterSettings` ([`FlutterSettings.java`](FlutterSettings.java))
- `interface FlutterSettings.Listener`
- `@VisibleForTesting static FlutterSettings.setInstance(FlutterSettings)`: installs a test override returned by `getInstance()`.

## Invariants
- Most FlutterSettings keys are hardcoded string constants, mapped to boolean/string values in IntelliJ's PropertiesComponent. Exception: the SDK-outdated acknowledgement key is built dynamically as `<key>_<versionText>_<beforeSunset|afterSunset>`.
- `isShowClosingLabels`/`setShowClosingLabels` are not stored in PropertiesComponent; they delegate entirely to the Dart plugin's `DartClosingLabelManager`.
- Most property setters trigger settingsChanged() on registered Listener objects via EventDispatcher. These do **not** notify listeners: `setShowClosingLabels`, `setFontPackages`, `setSdkVersionOutdatedWarningAcknowledged`, `setAndroidStudioBotAcknowledgedKey`, `setFilePathLoggingEnabled`.
- FlutterSettings acts as an application service singleton but can be overridden with a test instance via `setInstance(...)`.

## Side Effects
- FlutterSettings writes and reads to IntelliJ IDE's PropertiesComponent, which serializes configuration to disk.
- FlutterSettings.setShowClosingLabels mutates the external DartClosingLabelManager singleton state.
- Modifying properties in FlutterSettings notifies registered observers via EventDispatcher, executing potential side effects in other components.
