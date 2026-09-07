<!--* freshness: { reviewed: '2026-08-31' } *-->
# Flutter Daemon

## Overview
Communicates with the `flutter daemon` and manages Flutter app execution lifecycles (run, attach, hot reload) and connected devices.

## Interface
- `DaemonApi`
- `DaemonApi.RestartResult`
- `DaemonConsoleView`
- `DaemonEvent`
- `DeviceDaemon`
- `DeviceSelection`
- `DeviceService`
- `DeviceService.State`
- `DevToolsInstance`
- `DevToolsServerTask`
- `DevToolsService`
- `FlutterApp`
- `FlutterApp.FlutterAppListener`
- `FlutterApp.State`

## Invariants
- DaemonApi commands are assigned unique sequential IDs and tracked in a synchronized pending map.
- DaemonApi maintains a bounded ring buffer for the last 100 stderr lines.
- DevToolsServerTask attempts to connect up to 10 times with a 1500ms delay between retries.
- DeviceService maintains an atomic reference to the current DeviceSelection.
- FlutterApp state changes are debounced and thread-safe (via AtomicReference).
- FlutterApp hot reload and restart operations require a valid, non-null appId.

## Side Effects
- DaemonApi writes JSON commands to the Flutter daemon process's stdin and reads its stdout/stderr.
- DaemonConsoleView intercepts and filters daemon JSON events from output, printing normal output to the IDE console.
- DevToolsServerTask spawns background DevTools server processes and can trigger IDE warning notifications.
- DevToolsService asynchronously manages DevTools server tasks using the IDE's ProgressManager.
- DeviceDaemon executes the 'flutter daemon' command, handles its process lifecycle, and displays IDE error dialogs on repeated crashes.
- DeviceService spawns threads to watch for Flutter/Java SDK changes, manage the DeviceDaemon lifecycle, and polls for device changes.
- DeviceService dispatches device list change events on the Swing Event Dispatch Thread (EDT).
- FlutterApp starts 'flutter run' or 'attach' processes, adds entries to the IDE's LocalHistory for hot reloads/restarts, and registers/unregisters VM services via DartToolingDaemonService.
- FlutterApp schedules graceful process shutdown asynchronously before falling back to abrupt process destruction.
