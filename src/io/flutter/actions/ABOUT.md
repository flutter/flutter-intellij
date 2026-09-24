<!--* freshness: { reviewed: '2026-08-31' } *-->
# Flutter Actions

## Overview
Registers standard IDE UI actions (buttons, context menus) that trigger Flutter CLI commands or navigation.

## Interface
- `AttachDebuggerAction`
- `DeviceSelectorAction`
- `DeviceSelectorRefresherAction`
- `ExtractWidgetAction`
- `FlutterAppAction`
- `FlutterBuildActionGroup`
- `FlutterCleanAction`
- `FlutterDoctorAction`
- `FlutterExternalIdeActionGroup`
- `FlutterGettingStartedAction`
- `FlutterNewProjectAction`
- `FlutterPackagesAddAction`
- `FlutterPackagesExplorerActionGroup`
- `FlutterPackagesGetAction`
- `FlutterPackagesUpgradeAction`
- `FlutterRetargetAppAction`
- `FlutterSdkAction`
- `FlutterSubmitFeedback`
- `FlutterToolsActionGroup`
- `FlutterUpgradeAction`
- `OpenAndroidModule`
- `OpenEmulatorAction`
- `OpenInAndroidStudioAction`
- `OpenInAppCodeAction`
- `OpenInXcodeAction`
- `OpenSimulatorAction`
- `PackageDialogWrapper`
- `ProjectActions`
- `RefreshToolWindowAction`
- `ReloadAllFlutterApps`
- `ReloadAllFlutterAppsRetarget`
- `ReloadFlutterApp`
- `ReloadFlutterAppRetarget`
- `RestartAllFlutterApps`
- `RestartAllFlutterAppsRetarget`
- `RestartFlutterApp`
- `RestartFlutterAppRetarget`
- `RestartFlutterDaemonAction`
- `RunFlutterAction`
- `RunProfileFlutterApp`
- `RunReleaseFlutterApp`

## Invariants
- Most actions extend standard IntelliJ action classes (e.g., AnAction, DumbAwareAction) and specify execution thread constraints via getActionUpdateThread().
- Actions extending FlutterSdkAction require a valid Flutter SDK configuration in the project environment to execute commands.
- Actions extending FlutterAppAction verify the active state and reload support capabilities of a Flutter app before enabling UI interactions.
- ProjectActions binds AnAction instances strictly to the lifecycle and context of a Project via IntelliJ's UserData mappings.
- Device selector actions treat the list of discovered devices as immutable during menu construction to prevent race conditions or concurrent modifications.

## Side Effects
- Spawns and controls background subprocesses invoking the Flutter CLI (e.g., flutter run, clean, doctor) and device daemons.
- Triggers FileDocumentManager.getInstance().saveAllDocuments() to persist unsaved IDE edits to disk before initiating Flutter commands.
- Launches native external programs, including IDEs (Android Studio, Xcode) and mobile device emulators/simulators.
- Mutates the IDE presentation state by dynamically toggling toolbar components, icons, labels, popup menus, and enabled properties.
- Reports anonymous telemetry and user interaction analytics asynchronously via the Dart plugin's Analytics logger.
- Displays modal windows and UI dialogs directly to the user.
