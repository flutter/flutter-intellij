<!--* freshness: { reviewed: '2026-10-25' } *-->
# Dart VM Service Integration

## Overview
Manages interactions with the Dart VM Service to provide debugging capabilities (breakpoints, stepping, isolation management) and toggle Flutter service extensions.

## Interface
- [`DartExceptionBreakpointHandler`](DartExceptionBreakpointHandler.java)
- [`DartVmServiceBreakpointHandler`](DartVmServiceBreakpointHandler.java)
- [`DartVmServiceDebugProcess`](DartVmServiceDebugProcess.java)
- [`DartVmServiceListener`](DartVmServiceListener.java)
- [`IsolatesInfo`](IsolatesInfo.java) (with nested `IsolatesInfo.IsolateInfo`)
- [`ServiceExtensionDescription`](ServiceExtensionDescription.java)
- [`ServiceExtensionState`](ServiceExtensionState.java)
- [`ServiceExtensions`](ServiceExtensions.java)
- [`ToggleableServiceExtensionDescription`](ToggleableServiceExtensionDescription.java)
- [`VMServiceManager`](VMServiceManager.java)
- [`VmServiceConsumers`](VmServiceConsumers.java)
- [`VmServiceWrapper`](VmServiceWrapper.java)
- [`./frame/ABOUT.md`](frame/ABOUT.md) - Subpackage for stack frame evaluation and variable inspection

## Invariants
- IsolatesInfo maps isolate IDs strictly to IsolateInfo, preventing duplicates. Each IsolateInfo also caches an `Isolate` future (`getCachedIsolate`/`invalidateCache`) and tracks `breakpointsSet`/`shouldInitialResume`.
- VMServiceManager buffers extensions announced by `ServiceExtensionAdded` events in `pendingServiceExtensions` until the first `Flutter.Frame` or `Flutter.FirstFrame` event. Extensions found by the initial `getVM`/`getIsolate` scan are added immediately. The first-frame flag resets when the Flutter isolate exits.
- VmServiceWrapper checks that synchronous requests are not made on the EDT, under a read action, or on the websocket thread. Violations are only logged with `LOG.error`; the request still proceeds.

## Side Effects
- Modifies the running Flutter app's internal VM states (adding/removing breakpoints, pausing/resuming execution, dropping frames).
- Updates the local IDE debugger session representations (stacks, breakpoints presentation).
- Writes event and error outputs directly to the IDE's execution console.
- VMServiceManager toggles features actively running on the device via RPCs (e.g., debug paint, slow animations), and enables the on-device inspector extension when constructed.
- VMServiceManager detects the Flutter isolate, tracks registered services (`ServiceRegistered`/`ServiceUnregistered`), and routes `Flutter.Error` and Logging events to `FlutterConsoleLogManager`. Its `dispose()` is empty, so its VM service listener is never removed.
- Subscribes to pushed Dart VM event streams (not polling): VMServiceManager listens to Extension, Logging and Service; VmServiceWrapper listens to Debug, Isolate, and ToolEvent.
- `DartVmServiceListener` evaluates breakpoint condition and log expressions synchronously, blocking for up to 1 second per expression on a `Semaphore`.
