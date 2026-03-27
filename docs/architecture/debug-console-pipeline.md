# Debug Console Pipeline — Architecture Reference

This document describes how Flutter app output gets from the running process to the
IntelliJ debug console. It covers the full launch sequence, error display paths, and
known gaps. Keep this document in sync when modifying `LaunchState`, `FlutterApp`,
`DaemonApi`, `DaemonConsoleView`, `VMServiceManager`, or `FlutterConsoleLogManager`.

---

## Key classes and their roles

| Class | Package | Role |
|-------|---------|------|
| `LaunchState` | `io.flutter.run` | Orchestrates launch: creates app, console, debug session |
| `FlutterApp` | `io.flutter.run.daemon` | Central app state; owns process, console, VM service refs |
| `DaemonApi` | `io.flutter.run.daemon` | Parses flutter daemon JSON protocol from process stdout |
| `DaemonConsoleView` | `io.flutter.run.daemon` | Filters daemon JSON from console display |
| `StdoutJsonParser` | `io.flutter.utils` | Buffers stdout and separates JSON events from plain text |
| `VMServiceManager` | `io.flutter.vmService` | Manages VM service stream subscriptions |
| `VmServiceWrapper` | `io.flutter.vmService` | Wraps VM service; subscribes debug/isolate streams |
| `DartVmServiceDebugProcess` | `io.flutter.vmService` | XDebugProcess implementation; handles VM events |
| `FlutterConsoleLogManager` | `io.flutter.logging` | Renders structured Flutter.Error events to console |
| `DiagnosticsNode` | `io.flutter.logging` | Parses Flutter diagnostics JSON from VM events |

---

## Launch sequence

```
User clicks Run/Debug
      ↓
ExecutionManager.startRunProfile(config, executor)
      ↓
FlutterRunner.doExecute(state, env)
      ↓
LaunchState.launch(env)
      ↓
  1. myCreateAppCallback.createApp(device)  →  FlutterApp.start()
     - Builds `flutter run --machine -d <deviceId>` command line
     - Creates MostlySilentColoredProcessHandler
     - Creates DaemonApi
     - Calls api.listen(process, FlutterAppDaemonEventListener)
       → process.startNotify()    ← PROCESS STARTS HERE
     - Returns FlutterApp
      ↓
  2. setUpConsoleAndActions(app)
     - Creates DaemonConsoleView (via createConsole)
     - app.setConsole(console)
     - console.attachToProcess(app.getProcessHandler())  ← console receives output
     - Returns DefaultExecutionResult(console, handler, actions)
      ↓
  3. createDebugSession(env, app, result)   [if supportsDebugConnection]
     - XDebuggerManager.startSession(env, starter)
     - Starter creates FlutterDebugProcess(app, env, session, executionResult, ...)
     - Session calls FlutterDebugProcess.sessionInitialized()
     - Returns XDebugSession
      ↓
  4. Returns RunContentDescriptor
```

**Timing note**: `DaemonApi.listen()` calls `startNotify()` before `console.attachToProcess()`
is called. In practice this is safe because Flutter daemon startup output is small and
the console attaches quickly. But any listener added after `startNotify()` may miss
early output if there is a long pause.

---

## VM connection sequence

```
Flutter daemon emits app.debugPort event
      ↓
DaemonApi.onTextAvailable(STDOUT) → stdoutParser → dispatch → onAppDebugPort()
      ↓
app.setWsUrl(wsUri)
console.print("Debug service listening on " + wsUri, SYSTEM_OUTPUT)
      ↓
DartVmServiceDebugProcess connects to wsUri  [via ObservatoryConnector polling]
      ↓
VmServiceWrapper.handleDebuggerConnected()
  streamListen(VmService.DEBUG_STREAM_ID)
  streamListen(VmService.ISOLATE_STREAM_ID)
  optionally: streamListen("ToolEvent")
      ↓
FlutterDebugProcess.onVmConnected(vmService)
      ↓
FlutterViewMessages.sendDebugActive(project, app, vmService)
      ↓
  - new VMServiceManager(app, vmService)
      → streamListen(VmService.EXTENSION_STREAM_ID)
      → streamListen(VmService.LOGGING_STREAM_ID)
      → streamListen(VmService.SERVICE_STREAM_ID)
      → vmService.addVmServiceListener(this)
  - app.setVmServices(vmService, vmServiceManager)
      → app.getFlutterConsoleLogManager()     ← initialises FlutterConsoleLogManager
        → if isShowStructuredErrors:
            hasServiceExtension("ext.flutter.inspector.structuredErrors", cb)
            cb fires when extension is registered by Flutter framework:
              callBooleanExtension("ext.flutter.inspector.structuredErrors", true)
              → Flutter STOPS sending app.log for errors
              → Flutter sends Flutter.Error VM extension events instead
```

---

## Error display paths

### Path A — app.log daemon event (default; structured errors OFF or unavailable)

Used when `ext.flutter.inspector.structuredErrors` is not present or not yet enabled.
This is the path Chrome/web targets always use because the extension is not available
in web builds.

```
Flutter debugPrint("some error") or FlutterError.onError fallback
      ↓
flutter tool captures app stdout
      ↓
Daemon emits: [{"event":"app.log","params":{"log":"some error","error":true}}]
      ↓
DaemonApi.onTextAvailable(STDOUT) → stdoutParser → dispatch → FlutterAppDaemonEventListener.onAppLog()
      ↓
app.getConsole().print(message.log + "\n", ERROR_OUTPUT)
      ↓
DaemonConsoleView.print(text, ERROR_OUTPUT)
  → writeAvailableLines()   [flush any buffered stdout first]
  → super.print(text, ERROR_OUTPUT)  ← DISPLAYED IN RED ✓
```

### Path B — Flutter.Error VM extension event (structured errors ON)

Used when `ext.flutter.inspector.structuredErrors` is available and enabled. This is
the path used for Windows/macOS/Linux desktop and mobile targets. If this path fails
silently, NO error output reaches the console — the user sees nothing.

```
Flutter framework error → FlutterError.presentError()
      ↓
Flutter.Error event on VM service Extension stream
      ↓
VMServiceManager.onVmServiceReceived("Flutter.Error")
      ↓
app.getFlutterConsoleLogManager().handleFlutterErrorEvent(event)
      ↓
// DiagnosticsNode is constructed from the event JSON
// if isShowStructuredErrors:
queue.add(() -> {
  try {
    processFlutterErrorEvent(diagnosticsNode)    ← renders to console
  } catch (Throwable t) {
    LOG.warn("Error processing FlutterErrorEvent: " + t.getMessage())
    // ← SILENT FAILURE: no output shown to user if this throws
  }
})
      ↓
processFlutterErrorEvent():
  console.print(header, TITLE_CONTENT_TYPE)
  for property in diagnosticsNode.getInlineProperties():
    printDiagnosticsNodeProperty(property, ...)
  console.print(footer, TITLE_CONTENT_TYPE)
```

**Known silent failure risk** (see issue #8839): `DiagnosticsNode.getStyleMember()`
calls `DiagnosticsTreeStyle.valueOf(value.getAsString())` without a try/catch. If the
Flutter framework emits an unknown `style` value, `IllegalArgumentException` is thrown,
caught silently in the queue lambda, and no error is shown. `getLevelMember()` in the
same class does guard against this with `catch (IllegalArgumentException ignore)`.
Fix: apply the same guard to `getStyleMember()`, and add a fallback raw print in the
queue catch block so errors are never silently dropped.

### Path C — VM service WriteEvent (NOT currently active)

The Dart VM sends `WriteEvent` on the `Stdout` stream for `print()` and `stderr.write()`
calls from Dart code. `DartVmServiceDebugProcess.handleWriteEvent()` exists to handle
these, but **the `Stdout` stream is never subscribed**. This is dead code.

```
Dart print() / stderr.write()
      ↓
Dart VM sends WriteEvent on Stdout stream
      ↓
[Stdout stream NOT subscribed — event never delivered]
      ↓
DartVmServiceDebugProcess.handleWriteEvent(base64Data)  ← NEVER CALLED
```

Streams currently subscribed:
- `VMServiceManager`: `Extension`, `Logging`, `Service`
- `VmServiceWrapper`: `Debug`, `Isolate`, optionally `ToolEvent`
- **NOT subscribed**: `Stdout`, `Stderr`

If `Stdout` were subscribed and `handleWriteEvent` decoded and printed output as
`NORMAL_OUTPUT`, it would pass through `DaemonConsoleView`'s `StdoutJsonParser` on a
different thread (VM service WebSocket thread) from the process stdout listener thread,
creating a concurrency hazard (see threading section below).

---

## DaemonConsoleView — how output filtering works

`DaemonConsoleView` extends `ConsoleViewImpl` and intercepts `print()` to filter out
flutter daemon JSON protocol lines from the raw console display.

### print() routing

```
print(text, contentType)
    ├── !hasFlutterModule?       → return (swallow everything)
    ├── isVerboseLogging?        → super.print(text, contentType) and return
    ├── contentType != NORMAL_OUTPUT (e.g. ERROR_OUTPUT)?
    │     → writeAvailableLines()          [flush buffered stdout first]
    │     → super.print(text, contentType) [direct display — bypasses JSON filter]
    └── contentType == NORMAL_OUTPUT?
          → stdoutParser.appendOutput(text)
          → writeAvailableLines()
```

### writeAvailableLines()

```java
for (String line : stdoutParser.getAvailableLines()) {
  if (DaemonApi.parseAndValidateDaemonEvent(line.trim()) != null) {
    // Daemon JSON — swallow (already handled by DaemonApi)
    if (isVerboseLogging) LOG.info(line.trim());
  } else {
    if (!hasPrintedText && line.equals("\n")) continue;  // skip spurious leading newline
    hasPrintedText = true;
    super.print(line, ConsoleViewContentType.NORMAL_OUTPUT);
  }
}
```

A line is identified as a daemon event when it starts with `[{` and parses as valid
JSON with at least one non-null key.

### Threading hazard

`DaemonConsoleView.print(text, NORMAL_OUTPUT)` is called from two sources:
1. **Process stdout listener** (background reader thread) — daemon JSON + non-JSON text
2. **`handleWriteEvent()`** in `DartVmServiceDebugProcess` (VM service WebSocket thread)

Both call `stdoutParser.appendOutput()` without synchronization. `StdoutJsonParser` is
not thread-safe for concurrent writes. If Path C were active, this would be a race
condition. The safe mitigation is to print `WriteEvent` output as `ERROR_OUTPUT` (which
bypasses `stdoutParser`) or to synchronize access.

---

## FlutterApp state machine

```
STARTING → STARTED  →  RELOADING → STARTED   (hot reload cycle)
                     →  RESTARTING → STARTED  (hot restart)
         → TERMINATING → TERMINATED
```

`app.getConsole()` is non-null from the moment `setUpConsoleAndActions()` returns,
before the debug session is created. Daemon event handlers can call it safely.

---

## ObservatoryConnector

Bridge between the plugin and the Dart VM service WebSocket URL:

```java
public interface ObservatoryConnector {
  @Nullable String getWebSocketUrl();   // ws://127.0.0.1:PORT/... (null until started)
  @Nullable String getBrowserUrl();
  @Nullable String getRemoteBaseUrl();
  void onDebuggerPaused(@NotNull Runnable resume);
  void onDebuggerResumed();
}
```

`FlutterApp` implements this via an anonymous class. `DartVmServiceDebugProcess` polls
`getWebSocketUrl()` until it returns non-null, then initiates the VM service connection.

---

## StdoutJsonParser — line buffering detail

Buffers incoming text chunks and emits complete lines:
- `\n` — flush line
- `\r\n` (Windows) — flush line (tracks `isPotentialWindowsReturn` flag on `\r`)
- `[{...}]` — JSON daemon event: buffer until `}]` is detected, then flush as one unit
- `possiblyTerminatesJson()` — heuristic flush when `}]` is followed by an alphabetic
  character (handles adjacent JSON + non-JSON without newline separator)
- If not inside a JSON event (`!bufferIsJson`), flushes immediately on each `appendOutput()`
  so regular log text is never held back

---

## XDebugSession console vs. debugger tabs

IntelliJ debug UI shows:
- **Console** tab — app stdout/stderr, your `DaemonConsoleView`
- **Debugger** tab — breakpoints, variables, stack frames (XDebugger framework)

`getSession().getConsoleView()` returns the `DaemonConsoleView` (from
`FlutterDebugProcess.createConsole()` → `myExecutionResult.getExecutionConsole()`).

When `suppressDebugViews()` is called (Run mode rather than Debug mode), all tabs
except Console are removed, giving a clean run-window experience.
