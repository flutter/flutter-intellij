<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter Actions

## Overview
Registers standard IDE UI actions (buttons, context menus) that trigger Flutter CLI commands or navigation. Most actions are registered in [`plugin.xml`](../../../../resources/META-INF/plugin.xml); Android Studio-only actions are in [`studio-contribs.xml`](../../../../resources/META-INF/studio-contribs.xml).

## Interface
- [`AttachDebuggerAction`](AttachDebuggerAction.java)
- [`DeviceSelectorAction`](DeviceSelectorAction.java): toolbar device picker. Its popup holds an inner `SelectDeviceAction` per connected device, plus Open iOS Simulator (macOS), Android emulator, and Restart Daemon entries.
- [`DeviceSelectorRefresherAction`](DeviceSelectorRefresherAction.java)
- [`ExtractWidgetAction`](ExtractWidgetAction.java)
- [`FlutterAppAction`](FlutterAppAction.java): base class for actions bound to a running `FlutterApp`.
- [`FlutterBuildActionGroup`](FlutterBuildActionGroup.java): build menu; inner actions `AAR`, `APK`, `AppBundle`, `Bundle`, `Ios`, `Linux`, `Macos`, `Web`, `Windows` are registered individually in `plugin.xml`.
- [`FlutterCleanAction`](FlutterCleanAction.java)
- [`FlutterDoctorAction`](FlutterDoctorAction.java)
- [`FlutterExternalIdeActionGroup`](FlutterExternalIdeActionGroup.java): not registered (commented out in `plugin.xml`, see issue #7103).
- [`FlutterGettingStartedAction`](FlutterGettingStartedAction.java)
- [`FlutterNewProjectAction`](FlutterNewProjectAction.kt) (Kotlin): registered only in `studio-contribs.xml` (Android Studio only).
- [`FlutterPackagesAddAction`](FlutterPackagesAddAction.java)
- [`FlutterPackagesExplorerActionGroup`](FlutterPackagesExplorerActionGroup.java)
- [`FlutterPackagesGetAction`](FlutterPackagesGetAction.java)
- [`FlutterPackagesUpgradeAction`](FlutterPackagesUpgradeAction.java)
- [`FlutterRetargetAppAction`](FlutterRetargetAppAction.java)
- [`FlutterSdkAction`](FlutterSdkAction.java): base class for actions that run a Flutter SDK command.
- [`FlutterSubmitFeedback`](FlutterSubmitFeedback.java)
- [`FlutterToolsActionGroup`](FlutterToolsActionGroup.java)
- [`FlutterUpgradeAction`](FlutterUpgradeAction.java)
- [`OpenAndroidModule`](OpenAndroidModule.java): not registered; only loaded reflectively by `OpenInAndroidStudioAction`.
- [`OpenEmulatorAction`](OpenEmulatorAction.java)
- [`OpenInAndroidStudioAction`](OpenInAndroidStudioAction.java): not registered (commented out in `plugin.xml`, see issue #7103).
- [`OpenInAppCodeAction`](OpenInAppCodeAction.java)
- [`OpenInXcodeAction`](OpenInXcodeAction.java)
- [`OpenSimulatorAction`](OpenSimulatorAction.java)
- [`PackageDialogWrapper`](PackageDialogWrapper.java)
- [`ProjectActions`](ProjectActions.java)
- [`RefreshToolWindowAction`](RefreshToolWindowAction.java)
- [`ReloadAllFlutterApps`](ReloadAllFlutterApps.java)
- [`ReloadAllFlutterAppsRetarget`](ReloadAllFlutterAppsRetarget.java)
- [`ReloadFlutterApp`](ReloadFlutterApp.java)
- [`ReloadFlutterAppRetarget`](ReloadFlutterAppRetarget.java)
- [`RestartAllFlutterApps`](RestartAllFlutterApps.java)
- [`RestartAllFlutterAppsRetarget`](RestartAllFlutterAppsRetarget.java)
- [`RestartFlutterApp`](RestartFlutterApp.java)
- [`RestartFlutterAppRetarget`](RestartFlutterAppRetarget.java)
- [`RestartFlutterDaemonAction`](RestartFlutterDaemonAction.java)
- [`RunFlutterAction`](RunFlutterAction.java)
- [`RunProfileFlutterApp`](RunProfileFlutterApp.java)
- [`RunReleaseFlutterApp`](RunReleaseFlutterApp.java)

## Invariants
- Most actions extend standard IntelliJ action classes (e.g., AnAction, DumbAwareAction) and specify execution thread constraints via getActionUpdateThread().
- Actions extending FlutterSdkAction require a Flutter SDK. If none is configured, it shows a "configure SDK" dialog and reports `MISSING_SDK` analytics instead of running. When the event has no PubRoot, the command runs for every PubRoot in the project.
- Actions extending FlutterAppAction are enabled only when the app is started, the action's applicability check passes, and the app's mode supports reload. Each instance registers itself in ProjectActions under its action ID while the app is connected, and unregisters when it disconnects.
- ProjectActions binds AnAction instances strictly to the lifecycle and context of a Project via IntelliJ's UserData mappings.
- DeviceSelectorAction rebuilds its menu as a new list and swaps it into a `volatile` field in one step (copy-on-write), so menu construction never sees a partially updated list.

## Side Effects
- Spawns and controls background subprocesses invoking the Flutter CLI (e.g., flutter run, clean, doctor) and device daemons.
- Triggers FileDocumentManager.getInstance().saveAllDocuments() to persist unsaved IDE edits to disk before initiating Flutter commands.
- Launches native external programs, including Xcode, AppCode, and mobile device emulators/simulators. (The Android Studio launch path is currently unregistered.)
- Mutates the IDE presentation state by dynamically toggling toolbar components, icons, labels, popup menus, and enabled properties.
- Reports anonymous telemetry by calling the Dart plugin's `com.jetbrains.lang.dart.analytics.Analytics.report` directly and synchronously. Calls don't go through the fail-safe wrapper in [`analytics`](../analytics/ABOUT.md).
- Displays modal windows and UI dialogs directly to the user.
