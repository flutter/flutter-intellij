<!--* freshness: { reviewed: '2026-09-07' } *-->
# Flutter Settings

## Overview
Manages persistent IDE settings and preferences for the Flutter plugin.

## Interface
- `class FlutterSettings`
- `interface FlutterSettings.Listener`

## Invariants
- FlutterSettings keys are hardcoded string constants, mapped to boolean/string values in IntelliJ's PropertiesComponent.
- Property updates in FlutterSettings generally trigger settingsChanged() on registered Listener objects via EventDispatcher.
- FlutterSettings acts as an application service singleton but can be overridden with a testInstance.

## Side Effects
- FlutterSettings writes and reads to IntelliJ IDE's PropertiesComponent, which serializes configuration to disk.
- FlutterSettings.setShowClosingLabels mutates the external DartClosingLabelManager singleton state.
- Modifying properties in FlutterSettings notifies registered observers via EventDispatcher, executing potential side effects in other components.
