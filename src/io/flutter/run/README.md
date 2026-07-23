# Debugger Architecture Overview

## `io.flutter.run`

* `LaunchState` - launches a Flutter app in "machine" mode so that it
  sends and receives JSON messages.

* `LaunchState.Runner` - abstract class for a way of running a Flutter app using a Flutter SDK.

* `SdkRunConfig` - holds user-editable settings when using a Flutter SDK.
  Creates the form to edit them and the LaunchState to run them.

* `FlutterDebugProcess` -  subclass of `DartVmServiceDebugProcessZ` that handles Flutter hot reloading.

* `FlutterRunConfigurationProducer` - creates `SdkRunConfig` based on context (e.g.,
  active file).
* `FlutterRunConfigurationType` - singleton that determines if Flutter run configurations are
  appropriate for a given project and creates template `SdkRunConfig`s.
* `SdkRunner` - starts flutter app without debugging, when using a Flutter SDK.
* `SdkFields` - User-editable fields for starting a Flutter app.
* `FlutterConfigurationEditorForm` - form for editing fields in SdkFields.

## `io.flutter.run.daemon`

* `FlutterApp` - represents a running Flutter app.
* `FlutterAppListener` - callback for daemon lifecycle events.
* `DaemonApi` - defines protocol for sending JSON messages to running Flutter app's process.
* `DaemonEvent` - event received from running Flutter app's process.
* `DaemonEvent.Listener` - base class for receiving events from Flutter app's process.

* `DeviceService` - a project level singleton. It manages the device poller and holds the list of devices.
* `FlutterDevice` - snapshot of a connected Flutter device's configuration, based on last poll.

