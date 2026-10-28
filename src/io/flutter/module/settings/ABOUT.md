<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter Module Settings

## Overview
UI forms and data models for configuring project-specific and module-specific settings during 'flutter create'.

Key files:
- [FlutterCreateAdditionalSettingsFields.java](FlutterCreateAdditionalSettingsFields.java): assembles the wizard fields (description, project type, org, Android language, platforms, offline flag). It mirrors user input into a `FlutterCreateAdditionalSettings` model, and `getAdditionalSettings()` builds a fresh `FlutterCreateAdditionalSettings` from the current field values.
- [ProjectType.java](ProjectType.java) / [ProjectType.form](ProjectType.form): the project type combo box.
- [RadiosForm.java](RadiosForm.java) / [RadiosForm.form](RadiosForm.form): a two-option radio group, used for the Android language choice (Java vs. Kotlin). It drives `FlutterCreateAdditionalSettings.setKotlin`.
- [PlatformsForm.kt](PlatformsForm.kt): Kotlin UI DSL panel with one checkbox per target platform.
- [FlutterCreateParams.java](FlutterCreateParams.java) / [FlutterCreateParams.form](FlutterCreateParams.form): the "create project offline" checkbox.
- [SettingsHelpForm.java](SettingsHelpForm.java) / [SettingsHelpForm.form](SettingsHelpForm.form): help text and a getting-started link.
- [InitializeOnceBoolValueProperty.java](InitializeOnceBoolValueProperty.java): a minimal boolean property holder used by the platform settings.

## Interface
- `FlutterCreateAdditionalSettingsFields`
- `FlutterCreateParams`
- `InitializeOnceBoolValueProperty`
- `PlatformsForm`
- `ProjectType`
- `RadiosForm`
- `SettingsHelpForm` (`showGettingStarted()`, `adjustContrast(FlutterProjectType)`)

## Invariants
- `InitializeOnceBoolValueProperty.initialize()` takes effect only on its first call; later calls are ignored. The public `set()` can still change the value at any time. The class is standalone and does not extend an `AbstractProperty`, despite its Javadoc.
- InitializeOnceBoolValueProperty throws an Error if addConstraint or addListener are called.
- The available project types in ProjectType start from every `FlutterProjectType` value. Unless the `flutter.experimental.modules` system property is set, `IMPORT` is removed, and `MODULE` is also removed outside Android Studio. The SDK supplier passed to `ProjectType` is stored but unused.
- `PlatformsForm` defaults every platform to selected, and its checkboxes are always enabled.
- `getAdditionalSettings()` omits platforms for project types that don't use them (see `shouldIncludePlatforms()`), and never passes the web platform for `PLUGIN_FFI`.

## Side Effects
- `FlutterCreateParams.setInitialValues()` checks online/offline status by opening a raw TCP socket to `pub.dartlang.org:80` on the calling (UI) thread. If the connection fails, it preselects the offline checkbox.
- Selecting a new project type in FlutterCreateAdditionalSettingsFields automatically mutates the underlying FlutterCreateAdditionalSettings model and modifies the enabled state of UI fields. If a help form has been linked through `linkHelpForm`, it also calls `SettingsHelpForm.adjustContrast`. The only caller of `linkHelpForm` is the unused `io.flutter.project.FlutterModuleGroup`, so in practice this does not happen.
- SettingsHelpForm registers a listener that opens a URL in the system browser when the getting started link is clicked.
- `ProjectType.updateProjectTypes()` adds `SKELETON`, `PLUGIN_FFI` and `EMPTY_PROJECT` only if they are missing. Because the model already starts with every type, the call is effectively a no-op. The `remove*` helpers are unused.
