<!--* freshness: { reviewed: '2026-08-31' } *-->
# Flutter Module Creation

## Overview
Manages the IDE integration for creating new Flutter modules and projects, bridging the IntelliJ module wizard to the `flutter create` CLI.

## Interface
- `class FlutterGeneratorPeer`
- `class FlutterModuleBuilder extends ModuleBuilder`
- `class FlutterModuleBuilder.FlutterModuleWizardStep extends ModuleWizardStep implements Disposable`
- `enum FlutterProjectType`
- `./settings/ABOUT.md` - Subpackage for configuration UI components

## Invariants
- Module names must be valid Dart package names (lower_case_with_underscores), valid Dart identifiers, not Dart keywords, and not conflict with standard Flutter package dependencies.
- A valid Flutter SDK path is mandatory for project creation.
- If no previously known SDK paths exist, the UI defaults the SDK path to the FLUTTER_SDK environment variable if present.
- FlutterProjectType implicitly enforces whether platform selection is required via its requiresPlatform flag.

## Side Effects
- Executes 'flutter create' as a subprocess during module creation, generating files on disk.
- Modifies IntelliJ project configuration (adds an uncommitted 'Dart SDK' project library reference, commits newly created modules).
- Changes the IDE view (switches to ProjectViewPane) and opens the main.dart file after project creation.
- Spawns background threads to query configured SDK platforms whenever the SDK path is changed.
- Can prompt the user to invoke a 'Flutter Doctor' action if 'flutter create' fails.
- Dynamically loads and commits Android submodules if detected.
