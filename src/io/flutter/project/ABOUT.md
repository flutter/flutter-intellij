<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter Project Structure

## Overview
Manages the IDE's recognition of Flutter projects: Flutter-specific icons in the project view and project root detection when a project is imported from existing sources. Nothing here watches for later structure changes.

Key files:
- [FlutterIconProvider.java](FlutterIconProvider.java): `IconProvider`, registered as `iconProvider` in [plugin.xml](../../../../resources/META-INF/plugin.xml).
- [FlutterProjectStructureDetector.java](FlutterProjectStructureDetector.java): `ProjectStructureDetector` used by the "import project from existing sources" wizard, registered as `projectStructureDetector`.
- [FlutterModuleGroup.java](FlutterModuleGroup.java): module builder variants per project type.

## Interface
- `FlutterIconProvider`
- `FlutterModuleGroup`
- `FlutterProjectStructureDetector`

## Invariants
- FlutterIconProvider only provides icons when `FlutterModuleUtils.declaresFlutter(project)` is true for the project. It supplies:
  - the Flutter icon for pub roots that declare Flutter;
  - distinct icons for a root's `lib`, `android`, `ios`, `integration_test` and `.idea` directories;
  - a test overlay icon for `*_test.dart` files in test directories.
- FlutterModuleGroup extends `FlutterModuleBuilder` with static inner classes for different project types (App, Mod, Plugin, Package), with fixed weights for sorting. It is not referenced anywhere in `src/` and not registered in any `resources/META-INF/*.xml`, so it is effectively dead code.
- FlutterProjectStructureDetector classifies root directories as `FlutterProjectRoot` or `DartProjectRoot`, depending on whether their `PubRoot` declares Flutter. `setupProjectStructure` nevertheless assigns the Flutter module type to every root, Dart roots included.

## Side Effects
- `FlutterProjectStructureDetector.setupProjectStructure` subscribes to the application message bus (`ProjectManager.TOPIC`). When the imported project opens, it waits for `StartupManager.runAfterOpened` and `DumbService.smartInvokeLater`, then adds Android submodules through `FlutterModuleBuilder.addAndroidModule`. It also adds plugin example modules (`example/android/<name>_example_android.iml`).
- A pooled-thread task sleeps for 5 minutes and then disconnects the subscription if the project never opened (for example, the import was cancelled).
