# SDK Functionality in IntelliJ Plugins (Flutter Focus)

This design document details the expected functionality, intentional design choices, trade-offs, and architecture for Flutter SDK handling in the `flutter-intellij` plugin.

---

## 1. Objectives & Summary

We define and agree on the desired plugin functionality for Flutter SDK management to facilitate refactoring, bug fixes, and maintenance.

### Key Intentions & Design Decisions
- **Flutter SDK Mandates the Dart SDK:** In a Flutter project, the Flutter SDK dictates the Dart SDK location (`<flutter-sdk>/bin/cache/dart-sdk`). A separate Dart SDK configuration for Flutter projects is disallowed because version skew between the Flutter engine/framework and the Dart compiler causes broken `.dill` builds, incompatible package resolution, and analyzer failures.
- **`pub get` Triggering:** Changing the Flutter SDK path invalidates package mappings in `.dart_tool/package_config.json` (such as `package:flutter/...` pointing into the Flutter SDK). Therefore, Flutter SDK changes require running `flutter pub get`. Standard Dart SDK imports resolve against the global cache (`~/.pub-cache/`), making Flutter SDK changes uniquely dependent on `pub get`.
- **UI Threading & `pub get` Execution:** Running `pub get` automatically on SDK save has historically caused IDE UI freezes when executed synchronously on the Event Dispatch Thread (EDT). The preferred design judgment is to run `pub get` asynchronously off the EDT or prompt the user to trigger it.
- **Global Path Persistence & Hygiene:** Validated SDK paths are persisted globally in `FLUTTER_SDK_KNOWN_PATHS` so that subsequent projects and settings dialogs can suggest recently used SDKs. Upon saving, non-existent paths are pruned from persistent storage and the list is capped at a maximum of 5 recent valid paths.
- **Single-Owner SDK Change Detection:** The Flutter plugin assumes total ownership of SDK update notifications in Flutter projects, explicitly setting `DartSdkUpdateOption.DoNotCheck` to suppress redundant Dart plugin SDK update checks.
- **Symlink & Version Manager (FVM) Preservation:** Symlink paths (such as `.fvm/flutter_sdk`) are explicitly preserved as the configured path. In-memory `FlutterSdk` and `FlutterSdkVersion` caches must be invalidated when a symlink target change is detected.
- **Project-Wide SDK Scoping (Single SDK Per Project):** Flutter SDK settings are strictly project-wide (1 SDK per IntelliJ `Project`). Multi-module SDK version skew and mixed standalone Dart + Flutter projects in a single workspace are explicitly unsupported non-goals (users should open separate projects).
- **Scoped Editor Notifications:** SDK configuration banners ("Flutter SDK not configured") are strictly scoped to `.dart` and Flutter `pubspec.yaml` files. They must never appear on native Java, Kotlin, Swift, or other files.
- **Active Debug Sessions & Processes:** Changing the SDK path leaves existing run/debug sessions and daemons untouched. Users are responsible for stopping and restarting debug sessions after an SDK change.
- **Remote Development Non-Goal:** Remote Development (WSL, Remote SSH, Dev Containers) is currently not officially supported or tested.

---

## 2. Detailed Behavior & Validation Matrix

### New Project Creation
- Suggests previously used Flutter SDK paths or the `FLUTTER_SDK` environment variable.
- After project creation, saves the Flutter SDK path and registers the nested Dart SDK path (`<flutter-sdk>/bin/cache/dart-sdk`) with the Dart plugin.

### Changing SDK Location
- Managed via **Settings > Languages & Frameworks > Flutter**.
- In **Settings > Languages & Frameworks > Dart**, the Dart SDK path displays the nested path (`<flutter-sdk>/bin/cache/dart-sdk`) and is non-editable for Flutter projects.

### Upgrading SDK & Symlink Version Switches
- Upon detecting an SDK upgrade on disk (e.g., via CLI `flutter upgrade` or FVM version switch), the plugin invalidates cached SDK version info, restarts the Dart Analysis Server, and automatically triggers/prompts `pub get`.

### Invalid SDK Validation Rules

| Condition | IDE Action | Rationale |
| :--- | :--- | :--- |
| **SDK path is null or empty** | Block saving / proceeding. | Required setting. |
| **SDK path does not exist on disk** | Show error, block saving / proceeding. | Invalid filesystem path. |
| **Path exists, but missing Flutter binaries/packages** | Show error, block saving / proceeding. | Path must contain `/packages/flutter/pubspec.yaml` and `/bin/flutter`. |
| **Flutter SDK present, but missing bundled Dart SDK** | Show error, block saving / proceeding. | Path must contain `/bin/cache/dart-sdk/lib`. Freshly cloned SDKs must be initialized via CLI before configuring in IDE (future work: analytics & auto-bootstrap). |
| **SDK version too old for plugin** | Issue warning, allow proceed/save. | Warn that features may fail unpredictably; recommend upgrading SDK or downgrading plugin. |
| **SDK version newer than plugin/pre-release** | Allow proceed/save silently. | No upper bound restriction enforced; bleeding-edge builds are assumed functional. |

---

## 3. General Code Locations

When making changes to SDK functionality, refer to the following key components in the codebase:

- **SDK Instance & Versioning:**
  - [`FlutterSdk.java`](../../src/io/flutter/sdk/FlutterSdk.java) - Per-project SDK instance, version querying, and running SDK tools.
  - [`FlutterSdkVersion.java`](../../src/io/flutter/sdk/FlutterSdkVersion.java) - Parses and compares SDK version strings against capability thresholds.

- **SDK Discovery, Validation & Paths:**
  - [`FlutterSdkUtil.java`](../../src/io/flutter/sdk/FlutterSdkUtil.java) - Core validation (`isFlutterSdkHome`, `getErrorMessageIfWrongSdkRootPath`), configuring nested Dart SDK (`setFlutterSdkPath`), and known paths persistence (`getKnownFlutterSdkPaths`).

- **Settings UI:**
  - [`FlutterSettingsConfigurable.java`](../../src/io/flutter/sdk/FlutterSettingsConfigurable.java) - **Settings > Languages & Frameworks > Flutter** UI, path selection, and applying changes.

- **SDK Manager & Change Notifications:**
  - [`FlutterSdkManager.java`](../../src/io/flutter/sdk/FlutterSdkManager.java) - Listens for library table changes and fires `flutterSdkAdded` / `flutterSdkRemoved`.

- **New Project Wizard:**
  - [`FlutterModuleBuilder.java`](../../src/io/flutter/module/FlutterModuleBuilder.java) - Handles project creation commit steps.
  - [`FlutterGeneratorPeer.java`](../../src/io/flutter/module/FlutterGeneratorPeer.java) - Manages SDK selection combo box and path validation in wizard.

- **Editor Notifications:**
  - [`SdkConfigurationNotificationProvider.java`](../../src/io/flutter/inspections/SdkConfigurationNotificationProvider.java) - Banner prompting user to configure Flutter SDK when opening Dart files without an SDK set.

