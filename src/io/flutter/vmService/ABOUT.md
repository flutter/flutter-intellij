<!--* freshness: { reviewed: '2026-08-17' } *-->
# Dart VM Service Integration

## Overview
Manages interactions with the Dart VM Service to provide debugging capabilities (breakpoints, stepping, isolation management) and toggle Flutter service extensions.

## Interface
- `DartExceptionBreakpointHandler`
- `DartVmServiceBreakpointHandler`
- `DartVmServiceDebugProcess`
- `DartVmServiceListener`
- `IsolatesInfo`
- `ServiceExtensionDescription`
- `ServiceExtensionState`
- `ServiceExtensions`
- `ToggleableServiceExtensionDescription`
- `VMServiceManager`
- `VmServiceConsumers`
- `VmServiceWrapper`
- `./frame/ABOUT.md` - Subpackage for stack frame evaluation and variable inspection

## Invariants
- IsolatesInfo maps isolate IDs strictly to IsolateInfo, preventing duplicates.
- VMServiceManager buffers service extension requests until the first frame event (Flutter.Frame) is received.
- VmServiceWrapper ensures that synchronous requests to the VM Service are not blocking the UI thread (EDT) or read actions.

## Side Effects
- Modifies the running Flutter app's internal VM states (adding/removing breakpoints, pausing/resuming execution, dropping frames).
- Updates the local IDE debugger session representations (stacks, breakpoints presentation).
- Writes event and error outputs directly to the IDE's execution console.
- VMServiceManager toggles features actively running on the device via RPCs (e.g., debug paint, slow animations).
- Subscribes asynchronously to Dart VM event streams (e.g. Isolate, Debug, Extension, Logging) resulting in background polling.
