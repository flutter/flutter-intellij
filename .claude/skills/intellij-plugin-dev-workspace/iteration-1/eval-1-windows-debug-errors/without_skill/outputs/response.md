# Flutter Windows Desktop Debug Exceptions Not Appearing in IntelliJ Console

## Overview

When a Flutter Windows desktop app runs in debug mode via the flutter-intellij plugin, exceptions thrown by the Flutter framework should appear in the IntelliJ "Run" or "Debug" console. The fact that Chrome works but Windows does not points to a platform-specific divergence in how the plugin launches the process, attaches the debugger, or routes stderr/stdout from the running process.

## The Error Display Pipeline

### 1. Process Launch — `FlutterApp` / `FlutterLaunchTask`

The entry point is the run configuration execution chain. When the user hits "Debug", IntelliJ calls the plugin's `RunProfileState`, which delegates to a `FlutterLaunchTask`. This task builds the `flutter run` command line, starts the process via `GeneralCommandLine` / `OSProcessHandler`, and returns a `ProcessHandler` that IntelliJ attaches to the console.

For Chrome the device ID is `chrome`; for Windows desktop it is `windows`. Both call `flutter run`, but output handling diverges.

### 2. Process Handler — `OSProcessHandler` / `FlutterApp`

`OSProcessHandler` reads stdout and stderr from the child process and broadcasts `ProcessEvent`s to registered `ProcessListener`s. IntelliJ's console view is one such listener. Key plugin classes involved:

- **`FlutterApp`** — wraps the running `flutter run` process and acts as central coordinator.
- **`DaemonApi` / `FlutterDaemonService`** — communicates with the Flutter tool daemon (JSON-RPC over stdin/stdout). The daemon sends structured events including `app.log`, `app.progress`, and error events.
- **`FlutterConsoleFilter`** — post-processes console lines (e.g., makes Dart file URIs clickable) but does not suppress them.

### 3. Daemon vs. Direct Output — the Critical Branch

- **Chrome**: Flutter's web tooling often emits errors directly to **stderr** of the `flutter run` process, which flows straight through `OSProcessHandler` to the console.
- **Windows desktop**: Framework-level exceptions (the `FlutterError.onError` red-box output) travel through a **`app.log` daemon event** — a structured JSON message that the plugin must explicitly listen for and forward to the console.

If the `app.log` listener is not registered, the errors are silently dropped even though the Flutter tool is emitting them correctly.

## Most Likely Failure Points on Windows

### Failure Point A: `app.log` listener not wired up for desktop targets

The listener registration code may have a device-type guard that inadvertently excludes desktop:

```java
if (device.isWeb()) {
    // handle raw stderr
} else if (device.isMobile()) {
    // register app.log listener
}
// desktop falls through — no listener registered!
```

This is the highest-probability cause. The fix is ensuring the `app.log` registration runs for all device types, including desktop.

### Failure Point B: `ConsoleView` reference is null for the desktop debug session

After the debug session starts, the plugin attaches an `XDebugSession`. The `ConsoleView` used during the pre-debug launch phase may differ from the one shown in the Debug tool window. If the plugin resolves the console by looking up the `RunContentDescriptor` and that lookup fails for the desktop configuration, `console` is null. The handler then silently no-ops:

```java
if (console != null) {
    console.print(log, contentType);
}
// else: message is lost with no warning
```

### Failure Point C: CRLF line-ending differences on Windows

Windows uses CRLF. If the `OSProcessHandler` or the daemon JSON parser does not handle `\r\n` correctly, daemon messages may fail to parse, causing `app.log` events to be missed entirely. Less likely than A or B for JSON payloads, but worth ruling out.

## Where to Add the Fix

### Step 1: Locate daemon event registration

Find the method that registers `app.log` listeners — likely in `FlutterApp.java` in a method called after the app process starts (e.g., `onAppStarted`, `start`), or in the `LaunchState`/`FlutterLaunchTask` `execute()` override.

### Step 2: Register the listener unconditionally for all device types

```java
daemonApi.listenForAppLog(appId, (String log, boolean isError) -> {
    ConsoleView console = getConsoleView();
    if (console == null) {
        logger.warn("FlutterApp: ConsoleView null on app.log for device: "
            + device.deviceId());
        return;
    }
    console.print(log + "\n",
        isError ? ConsoleViewContentType.ERROR_OUTPUT
                : ConsoleViewContentType.NORMAL_OUTPUT);
});
```

Remove any `device.isWeb()` or `device.isMobile()` guard that wraps this registration.

### Step 3: Verify ConsoleView resolution for desktop

If the diagnostic log in Step 2 fires (console is null), trace how the `ConsoleView` is passed to `FlutterApp` for desktop vs. mobile/web configurations. The desktop run configuration likely takes a different code path during `RunContentDescriptor` creation and does not inject the console reference.

### Step 4: Cross-check the event name

Add a temporary catch-all to confirm what event names the Flutter tool actually emits on Windows:

```java
daemonApi.listenForAllEvents(event -> {
    if (event.method.contains("log") || event.method.contains("error")) {
        logger.info("Daemon event: " + event.method + " / " + event.params);
    }
});
```

Check `idea.log` after triggering an exception on Windows to confirm the event name matches what the listener is registered for.

## Summary

| Component | Role | Failure risk on Windows |
|---|---|---|
| `FlutterLaunchTask.execute()` | Builds command, starts process | Low |
| `OSProcessHandler` | Reads raw stdout/stderr | Medium (CRLF) |
| `AppDaemonApi` / `app.log` listener | Forwards errors to console | **High — most likely root cause** |
| `FlutterApp.getConsoleView()` | Resolves active `ConsoleView` | **High** |
| `FlutterConsoleFilter` | Hyperlink post-processing | Low |

The most actionable fix is to audit where `app.log` daemon event listeners are registered, ensure registration is not gated on device type in a way that excludes desktop, and add null-safety diagnostics on the `ConsoleView` resolution path.
