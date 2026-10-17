<!--* freshness: { reviewed: '2026-08-31' } *-->
# vmServiceDrivers Element

## Overview
Provides data model elements for the Dart VM Service Protocol. These classes wrap JSON objects and arrays returned by the VM service.

## Interface
- Base classes: `Element`, `Response`, `ElementList` (generic array wrapper over `JsonArray`), `RPCError`
- Protocol enumerations: `CodeKind`, `ErrorKind`, `EventKind`, `ExceptionPauseMode`, `FrameKind`, `InstanceKind`, `SentinelKind`, `SourceReportKind`, `StepOption`
- Data model classes wrapping `JsonObject` including `AllocationProfile`, `Breakpoint`, `ClassRef`, `Event`, `Instance`, `Isolate`, `Script`, `VM`, etc.

## Invariants
- Base classes `Element`, `ElementList`, and `RPCError` are handwritten foundational classes; model elements are auto-generated wrappers over `JsonObject`.
- `ElementList` wraps `com.google.gson.JsonArray`, whereas data model classes wrap `com.google.gson.JsonObject`.
- Property getters act purely on the underlying JSON element, handling missing or null values gracefully by returning default values (null, false, -1).

## Side Effects
- None. The objects are pure read-only data wrappers with no mutating methods or system side effects.
