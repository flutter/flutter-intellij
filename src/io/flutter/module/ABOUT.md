<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter Module Creation

## Overview
Manages the IDE integration for creating new Flutter modules and projects, bridging the IntelliJ module wizard to the `flutter create` CLI.

Key files:
- [FlutterModuleBuilder.java](FlutterModuleBuilder.java): the `ModuleBuilder` that validates settings, runs `flutter create`, and commits the new module.
- [FlutterGeneratorPeer.java](FlutterGeneratorPeer.java) and [FlutterGeneratorPeer.form](FlutterGeneratorPeer.form): the wizard panel holding the SDK path combo box and the additional settings fields.
- [FlutterProjectType.java](FlutterProjectType.java): the project kinds `APP`, `PLUGIN`, `PACKAGE`, `MODULE`, `SKELETON`, `PLUGIN_FFI`, `EMPTY_PROJECT` and `IMPORT`. Each has a title, a `flutter create` argument, and a `requiresPlatform` flag.

## Interface
- `class FlutterGeneratorPeer`
- `class FlutterModuleBuilder extends ModuleBuilder`
  - `static runFlutterCreateWithProgress(...)`: runs `flutter create` behind a modal, indeterminate progress dialog and returns the resulting `PubRoot`.
  - `static addAndroidModule(...)` / `static addAndroidModuleFromFile(...)`: load an Android `.iml` module into the project. `commitModule` does not call them. They are used by [FlutterInitializer](../FlutterInitializer.java) and [FlutterProjectStructureDetector](../project/FlutterProjectStructureDetector.java).
  - `setFlutterSdkPath(String)`, `getSettingsField()`
- `class FlutterModuleBuilder.FlutterModuleWizardStep extends ModuleWizardStep implements Disposable`
- `enum FlutterProjectType`
- [`settings/`](settings/ABOUT.md) - Subpackage for configuration UI components

## Invariants
- Module names must be valid Dart package names (lower_case_with_underscores), valid Dart identifiers, not Dart keywords, and not conflict with standard Flutter package dependencies.
- A valid Flutter SDK path is mandatory for project creation.
- `validateSettings` also requires that the description does not contain `": "`, that the org does not end with `.` and is a valid Android package name, and that at least one platform is selected when platforms apply.
- The SDK combo is populated when it is first shown (`UiNotifyConnector.doWhenFirstShown`). If it is still empty after `FlutterSdkUtil.addKnownSDKPathsToCombo`, it defaults to the `FLUTTER_SDK` environment variable if present.
- Platform handling is enforced outside this package. `FlutterCreateAdditionalSettingsFields.shouldIncludePlatforms()` decides with its own hardcoded switch whether the platform fields apply. [FlutterCreateAdditionalSettings](../sdk/FlutterCreateAdditionalSettings.java) uses `FlutterProjectType.requiresPlatform` to default to `android,ios` when no platform was chosen.

## Side Effects
- Executes `flutter create` as a subprocess during module creation, generating files on disk. `runFlutterCreateWithProgress` first disables the Gradle project migration notification.
- Modifies IntelliJ project configuration (adds an uncommitted 'Dart SDK' project library reference, commits newly created modules).
- `commitModule` records the SDK path through `FlutterSdkUtil.updateKnownSdkPaths`.
- `validate()` deletes the destination project directory when settings validation fails.
- Changes the IDE view (switches to ProjectViewPane) after project creation. `FlutterModuleUtils.autoShowMain` then opens `PubRoot.getFileToOpen()` (`lib/main.dart`, or else the first child of `lib/`). It looks the file up on a pooled thread and opens it once the IDE is in smart mode.
- `FlutterGeneratorPeer.fillSdkCache` queries the SDK's configured platforms on a background thread at startup and on SDK combo box actions. Editing the path text only triggers `validate()`.
- Can prompt the user to invoke a 'Flutter Doctor' action if 'flutter create' fails.
