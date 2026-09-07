<!--* freshness: { reviewed: '2026-08-17' } *-->
# Flutter Module Settings

## Overview
UI forms and data models for configuring project-specific and module-specific settings during 'flutter create'.

## Interface
- `FlutterCreateAdditionalSettingsFields`
- `FlutterCreateParams`
- `InitializeOnceBoolValueProperty`
- `PlatformsForm`
- `ProjectType`
- `RadiosForm`
- `SettingsHelpForm`

## Invariants
- InitializeOnceBoolValueProperty can only be initialized once via initialize(); subsequent calls are ignored.
- InitializeOnceBoolValueProperty throws an Error if addConstraint or addListener are called.
- The available project types in ProjectType depend on the IDE environment and experimental system properties.

## Side Effects
- FlutterCreateParams.setInitialValues() makes a synchronous network request to pub.dartlang.org to determine online/offline status.
- Selecting a new project type in FlutterCreateAdditionalSettingsFields automatically mutates the underlying FlutterCreateAdditionalSettings model and modifies the visibility/enabled state of UI fields.
- SettingsHelpForm registers a listener that opens a URL in the system browser when the getting started link is clicked.
- ProjectType.updateProjectTypes() mutates the internal combo box model by adding new FlutterProjectType options.
