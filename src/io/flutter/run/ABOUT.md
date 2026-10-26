<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter Run Execution

## Overview
Configures, launches, and manages Flutter application execution processes (Run, Debug, Attach) from within the IDE. See [README.md](README.md) for a short architecture walkthrough of the run/debug classes.

## Interface
- [`AttachState`](AttachState.java): `LaunchState` subclass used for `flutter attach`.
- [`FlutterConfigurationEditorForm`](FlutterConfigurationEditorForm.java) (+ [`.form`](FlutterConfigurationEditorForm.form)): run-config editor UI for `SdkFields`.
- [`FlutterDebugProcess`](FlutterDebugProcess.java)
- [`FlutterDebugProcessActions`](FlutterDebugProcessActions.java)
- [`FlutterDebugSessionUtils`](FlutterDebugSessionUtils.java)
- [`FlutterDevice`](FlutterDevice.java)
- [`FlutterLaunchMode`](FlutterLaunchMode.java)
- [`FlutterPopFrameAction`](FlutterPopFrameAction.java)
- [`FlutterPositionMapper`](FlutterPositionMapper.java)
- [`FlutterReloadManager`](FlutterReloadManager.java)
- [`FlutterRunConfigurationProducer`](FlutterRunConfigurationProducer.java)
- [`FlutterRunConfigurationType`](FlutterRunConfigurationType.java)
- [`FlutterRunner`](FlutterRunner.java): the `LaunchState.Runner<SdkRunConfig>` program runner; runs only when a Flutter SDK is configured.
- [`FlutterRunNotifications`](FlutterRunNotifications.java)
- [`LaunchState`](LaunchState.java), with nested `LaunchState.Runner` (base program runner), `LaunchState.CreateAppCallback` (creates the `FlutterApp` for a device) and `LaunchState.RunConfig`.
- [`MainFile`](MainFile.java)
- [`ObservatoryFile`](ObservatoryFile.java)
- [`OpenDevToolsAction`](OpenDevToolsAction.java)
- [`SdkAttachConfig`](SdkAttachConfig.java): extends `SdkRunConfig` for attach sessions.
- [`SdkFields`](SdkFields.java)
- [`SdkRunConfig`](SdkRunConfig.java), with nested `SdkRunConfig.RecursiveDeleter`.
- [`./common/ABOUT.md`](common/ABOUT.md)
- [`./coverage/ABOUT.md`](coverage/ABOUT.md)
- [`./daemon/ABOUT.md`](daemon/ABOUT.md)
- [`./test/ABOUT.md`](test/ABOUT.md)

## Invariants
- A LaunchState must have a selected FlutterDevice to launch an app. LaunchState does not start the process itself: it delegates to the `CreateAppCallback` supplied by `SdkRunConfig`/`SdkAttachConfig` (which calls `FlutterApp.start`), then builds the console and the run/debug content descriptor.
- FlutterReloadManager reload-on-save only runs when `FlutterSettings.isReloadOnSave()` is on and the project has a Flutter module. On "save all" it silently skips the reload if *any* open, non-empty Dart editor in the project contains a `PsiErrorElement`. It also ignores files under the IDE config directory (scratch files) and files whose timestamp is older than 500ms. A final check of the saved file shows a "Reload not performed" notification on analysis errors.
- FlutterDebugProcess acts as the Dart VM Service debugging process for both 'Run' and 'Debug' modes, as the debug connection is required to support Hot Reload.
- SdkRunConfig entrypoints must reside within a valid Flutter pub root.
- The Flutter SDK and Dart SDK must be correctly configured in the project to launch an app.

## Side Effects
- FlutterReloadManager listens for IDE save events, triggers document saves, and issues hot reload/restart commands to running instances.
- SdkRunConfig recursively deletes build directory files matching `*.{fingerprint,dill}` via `RecursiveDeleter` prior to launch if launch parameters mismatch previous cached parameters, writing a last_build_run.json file to disk.
- SdkRunConfig (`MECHANISM_FLUTTER_APP`) and SdkAttachConfig (`MECHANISM_FLUTTER_ATTACH`) record analytics for run or debug sessions.
- LaunchState saves all documents, sets up a console view and updates the execution environment.
- LaunchState.launch calls `FlutterDevice.bringToFront()` on every launch. For iOS emulators only, it delegates to [`XcodeUtils.openSimulator`](../sdk/XcodeUtils.java) to bring the simulator to the foreground on macOS.
