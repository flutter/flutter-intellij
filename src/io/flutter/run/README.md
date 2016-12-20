# Debugger Architecture Overiew

## `io.flutter.run`

* `FlutterAppState` - a `RunProfileState` that puts together and starts Flutter command-line processes.
* `FlutterAppStateBase` - abstract base class for `FlutterAppState` (appears to be copied from
  `DartCommandLineRunningState`)
* `FlutterRunConfiguration` - creates:
  * `FlutterRunnerParameters`
  * `FlutterConfigurationEditorForm`
  * `FlutterAppState`
* `FlutterDebugProcess` -  subclass of `DartVmServiceDebugProcessZ` that registers UI actions corresponding
  to the current launch configuration (run vs. debug).
* `FlutterRunConfigurationBase` - abstract base class for `FlutterRunConfiguration` (appears to be
  copied from `DartRunConfigurationBase`).
* `FlutterRunConfigurationProducer` - creates `FlutterRunConfiguration` based on context (e.g.,
  active file).
* `FlutterRunConfigurationType` - singleton that determines if Flutter run configurations are
  appropriate for a given project and creates
  * `FlutterConfigurationFactory`
  * template `FlutterRunConfiguration`s
* `FlutterRunner` -
  * defines timeout (overriding base class)
  * determines if an executor (run or debug) can run based on device connection status
  * executes run (via current `FlutterAppState`)
* `FlutterRunnerBase` - copied from the Dart plugin and modified to use `DartVmServiceDebugProcessZ`
  to control the debugger, and to define `ObservatoryConnector`.
* `FlutterRunnerParameters` - encapsulates configuration options for Flutter runs.
* `FlutterConfigurationEditorForm` - form for editing run configuration settings.

## `io.flutter.run.daemon`

* `FlutterDevice` - interface representing a connected Flutter device.
* `DaemonJsonInputFilterProvider` - trims daemon console output.
* `DaemonListener` - callback for daemon lifecycle events.
* `FlutterApp` - represents a running Flutter app.
* `FlutterDaemonService` - a project level singleton. It manages the device poller and
    helps launch apps
* `FlutterDaemonController`
  * starts a Flutter daemon process, as an external OS-level process.
  * reads from the process standard output and writes to the process standard input, using JSON
    format to control the daemon.
  * defines classes (instances of a base `FlutterJsonObject` that process JSON).
* `FlutterDaemonControllerHelper` - manages running Flutter apps.
  * starts apps
  * processes daemon JSON input and dispatches handling to appropriate pending Flutter commands.

## `io.flutter.run.bazel`

* `FlutterBazelAppState` - a subclass of `FlutterAppStateBase` specialized for launching Flutter Bazel targets
* `FlutterBazelRunConfiguration` - a run configuration for Bazel targets; creates FlutterBazelAppState and FlutterBazelConfigurationEditorForm
* `FlutterBazelRunConfigurationType` - creates Bazel run configurations
* `FlutterBazelRunner` - a subclass of `FlutterRunner` that customizes the enablement logic
* `FlutterBazelConfigurationEditorForm` - the UI for Bazel run configurations
