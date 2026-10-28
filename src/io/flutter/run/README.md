<!--* freshness: { reviewed: '2026-10-25' } *-->
# Debugger Architecture Overview

See [ABOUT.md](ABOUT.md) for the package's invariants and side effects.

## `io.flutter.run`

* [`LaunchState`](LaunchState.java) - the `CommandLineState` for a Flutter run. It selects the
  device, asks its `CreateAppCallback` to start the app (the callback from `SdkRunConfig` /
  `SdkAttachConfig` calls `FlutterApp.start`, which runs `flutter` in "machine" mode so that it
  sends and receives JSON messages), then sets up the console and the run/debug content descriptor.

* `LaunchState.Runner` - abstract class for a way of running a Flutter app using a Flutter SDK.
* [`AttachState`](AttachState.java) - `LaunchState` subclass for `flutter attach`.

* [`SdkRunConfig`](SdkRunConfig.java) - holds user-editable settings when using a Flutter SDK.
  Creates the form to edit them and the LaunchState to run them.
* [`SdkAttachConfig`](SdkAttachConfig.java) - `SdkRunConfig` subclass for attaching to a running app.

* [`FlutterDebugProcess`](FlutterDebugProcess.java) - subclass of `DartVmServiceDebugProcess` that handles Flutter hot reloading.
* [`FlutterPositionMapper`](FlutterPositionMapper.java) - maps between IDE source positions and VM script locations.
* [`FlutterDebugProcessActions`](FlutterDebugProcessActions.java), [`FlutterPopFrameAction`](FlutterPopFrameAction.java),
  [`FlutterDebugSessionUtils`](FlutterDebugSessionUtils.java) - debugger toolbar actions and debug-session helpers.

* [`FlutterRunConfigurationProducer`](FlutterRunConfigurationProducer.java) - creates `SdkRunConfig` based on context (e.g.,
  active file).
* [`FlutterRunConfigurationType`](FlutterRunConfigurationType.java) - singleton that determines if Flutter run configurations are
  appropriate for a given project and creates template `SdkRunConfig`s.
* [`FlutterRunner`](FlutterRunner.java) - the program runner (`LaunchState.Runner<SdkRunConfig>`) for Flutter run configurations.
* [`SdkFields`](SdkFields.java) - User-editable fields for starting a Flutter app.
* [`FlutterConfigurationEditorForm`](FlutterConfigurationEditorForm.java) - form for editing fields in SdkFields.
* [`MainFile`](MainFile.java) - validates and resolves the Dart entrypoint file of a run config.
* [`FlutterLaunchMode`](FlutterLaunchMode.java) - debug/profile/release mode stored in the execution environment.
* [`FlutterDevice`](FlutterDevice.java) - snapshot of a connected Flutter device's configuration, as last reported by the device daemon.
* [`FlutterReloadManager`](FlutterReloadManager.java) - hot reload on save and reload/restart notifications.
* [`FlutterRunNotifications`](FlutterRunNotifications.java), [`OpenDevToolsAction`](OpenDevToolsAction.java) - run notifications and the open-DevTools action.
* [`ObservatoryFile`](ObservatoryFile.java) - a specific version of a Dart script as downloaded from the VM service (a new version appears after each hot reload).

## `io.flutter.run.daemon`

* [`FlutterApp`](daemon/FlutterApp.java) - represents a running Flutter app.
* `FlutterApp.FlutterAppListener` - nested callback interface for app lifecycle events.
* [`DaemonApi`](daemon/DaemonApi.java) - defines protocol for sending JSON messages to running Flutter app's process.
* [`DaemonEvent`](daemon/DaemonEvent.java) - event received from running Flutter app's process.
* `DaemonEvent.Listener` - package-private interface for receiving events from Flutter app's process.
* [`DaemonConsoleView`](daemon/DaemonConsoleView.java) - console that filters daemon JSON out of the app output.

* [`DeviceService`](daemon/DeviceService.java) - a project level singleton that holds the device list and selection. It runs a
  [`DeviceDaemon`](daemon/DeviceDaemon.java) (`flutter daemon`) through a `Refreshable` and updates devices from the daemon's
  device events (there is no poller). [`DeviceSelection`](daemon/DeviceSelection.java) is the immutable device-list/selection snapshot.
* [`DevToolsService`](daemon/DevToolsService.java), [`DevToolsServerTask`](daemon/DevToolsServerTask.java),
  [`DevToolsInstance`](daemon/DevToolsInstance.java) - locate the DevTools server (normally the one started by the Dart plugin).

## Subpackages

* [`common/`](common/ABOUT.md) - shared test/run helpers (`RunMode`, `ConsoleProps`, test line markers).
* [`coverage/`](coverage/ABOUT.md) - coverage runner and LCOV parsing for Flutter tests.
* [`test/`](test/ABOUT.md) - Flutter test run configurations.
