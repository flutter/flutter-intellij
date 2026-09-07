<!--* freshness: { reviewed: '2026-08-31' } *-->
# Flutter Run Execution

## Overview
Configures, launches, and manages Flutter application execution processes (Run, Debug, Attach) from within the IDE.

## Interface
- `AttachState`
- `FlutterConfigurationEditorForm`
- `FlutterDebugProcess`
- `FlutterDebugProcessActions`
- `FlutterDebugSessionUtils`
- `FlutterDevice`
- `FlutterLaunchMode`
- `FlutterPopFrameAction`
- `FlutterPositionMapper`
- `FlutterReloadManager`
- `FlutterRunConfigurationProducer`
- `FlutterRunConfigurationType`
- `FlutterRunner`
- `FlutterRunNotifications`
- `LaunchState`
- `MainFile`
- `ObservatoryFile`
- `OpenDevToolsAction`
- `SdkAttachConfig`
- `SdkFields`
- `SdkRunConfig`
- `./common/ABOUT.md`
- `./coverage/ABOUT.md`
- `./daemon/ABOUT.md`
- `./test/ABOUT.md`

## Invariants
- A LaunchState must have a selected FlutterDevice to launch an app.
- FlutterReloadManager only triggers hot reloads on save if there are no syntax errors in the currently edited Dart file.
- FlutterDebugProcess acts as the Dart VM Service debugging process for both 'Run' and 'Debug' modes, as the debug connection is required to support Hot Reload.
- SdkRunConfig entrypoints must reside within a valid Flutter pub root.
- The Flutter SDK and Dart SDK must be correctly configured in the project to launch an app.

## Side Effects
- FlutterReloadManager listens for IDE save events, triggers document saves, and issues hot reload/restart commands to running instances.
- SdkRunConfig recursively deletes build directory files matching `*.{fingerprint,dill}` via `RecursiveDeleter` prior to launch if launch parameters mismatch previous cached parameters, writing a last_build_run.json file to disk.
- SdkRunConfig records analytics for run or debug sessions.
- LaunchState sets up a console view and updates the execution environment.
- FlutterDevice.bringToFront() invokes system commands to bring the iOS simulator app to the foreground on macOS.
