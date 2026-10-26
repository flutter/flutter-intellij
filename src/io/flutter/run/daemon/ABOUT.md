<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter Daemon

## Overview
Communicates with the `flutter daemon` and manages Flutter app execution lifecycles (run, attach, hot reload) and connected devices.

## Interface
- [`DaemonApi`](DaemonApi.java)
- `DaemonApi.RestartResult`
- [`DaemonConsoleView`](DaemonConsoleView.java)
- [`DaemonEvent`](DaemonEvent.java) (with the package-private `DaemonEvent.Listener` interface)
- [`DeviceDaemon`](DeviceDaemon.java) (package-private; nested `DaemonCrashReporter` dialog)
- [`DeviceSelection`](DeviceSelection.java)
- [`DeviceService`](DeviceService.java)
- `DeviceService.State`
- [`DevToolsInstance`](DevToolsInstance.java)
- [`DevToolsServerTask`](DevToolsServerTask.java)
- [`DevToolsService`](DevToolsService.java)
- [`FlutterApp`](FlutterApp.java)
- `FlutterApp.FlutterAppListener`
- `FlutterApp.State`
- `FlutterAppDaemonEventListener` (package-private, in `FlutterApp.java`): handles app daemon events, including registering/unregistering the app's VM service with DTD.

## Invariants
- DaemonApi commands are assigned unique sequential IDs and tracked in a synchronized pending map.
- DaemonApi maintains a bounded ring buffer for the last 100 stderr lines.
- DevToolsServerTask waits for the Dart plugin's `DartDevToolsService` to report a DevTools host/port, checking up to 10 times with a 1500ms delay between checks. It does not normally start DevTools itself.
- DeviceService maintains an atomic reference to the current DeviceSelection.
- FlutterApp state changes are debounced and thread-safe (via AtomicReference).
- FlutterApp hot reload and restart operations require a valid, non-null appId.

## Side Effects
- DaemonApi writes JSON commands to the Flutter daemon process's stdin and reads its stdout/stderr.
- DaemonConsoleView intercepts and filters daemon JSON events from output, printing normal output to the IDE console.
- DevToolsServerTask spawns a local DevTools server process only in local-development mode (registry key `flutter.local.devtools.dir` set), and can trigger IDE warning notifications.
- DevToolsService asynchronously manages DevTools server tasks using the IDE's ProgressManager.
- DeviceDaemon executes the 'flutter daemon' command (setting `ANDROID_HOME` and analytics environment via `Analytics.updateEnvironment`), handles its process lifecycle, and displays IDE error dialogs on repeated crashes.
- DeviceService does not poll: the device list is updated from DeviceDaemon device events. It refreshes the daemon on Flutter SDK add/remove and project JDK changes, and also refreshes [`AndroidEmulatorManager`](../../sdk/AndroidEmulatorManager.java) when starting a daemon. If the daemon dies it is restarted immediately unless it was restarted in the last 20s (then a warning is shown); an explicit `restart()` shuts down and restarts after 4s.
- DeviceService dispatches device list change events on the Swing Event Dispatch Thread (EDT).
- FlutterApp starts 'flutter run' or 'attach' processes, adds entries to the IDE's LocalHistory for hot reloads/restarts, and registers/unregisters VM services via DartToolingDaemonService.
- FlutterApp schedules graceful process shutdown asynchronously before falling back to abrupt process destruction.
