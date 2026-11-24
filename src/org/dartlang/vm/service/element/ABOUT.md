<!--* freshness: { reviewed: '2026-11-07' } *-->
# vmServiceDrivers Element

## Overview
Provides data model elements for the Dart VM Service Protocol. These classes wrap JSON objects and arrays returned by the VM service.

## Interface
- Base classes: [`Element`](Element.java), [`Response`](Response.java), [`ElementList`](ElementList.java) (generic array wrapper over `JsonArray`), [`RPCError`](RPCError.java)
- Object hierarchy: [`Obj`](Obj.java) / [`ObjRef`](ObjRef.java) and their many `*Ref` counterparts (e.g. [`InstanceRef`](InstanceRef.java), [`LibraryRef`](LibraryRef.java), [`NullRef`](NullRef.java)); [`Sentinel`](Sentinel.java) for collected/expired objects.
- Simple responses: [`Success`](Success.java), [`Version`](Version.java)
- Protocol enumerations: `CodeKind`, `ErrorKind`, `EventKind`, `ExceptionPauseMode`, `FrameKind`, `InstanceKind`, `SentinelKind`, `SourceReportKind`, `StepOption`
- Data model classes wrapping `JsonObject` including [`AllocationProfile`](AllocationProfile.java), [`Breakpoint`](Breakpoint.java), [`Event`](Event.java), [`Instance`](Instance.java), [`Isolate`](Isolate.java), [`Script`](Script.java), [`VM`](VM.java), and CPU profiling types such as [`CpuSamples`](CpuSamples.java).

## Invariants
- Base classes `Element`, `ElementList`, and `RPCError` are foundational classes. `Response` and the model elements originated as generated wrappers over `JsonObject`, but the complete Java model is now maintained directly in this repository.
- `ElementList` wraps `com.google.gson.JsonArray`, whereas data model classes wrap `com.google.gson.JsonObject`.
- Missing or JSON-null values map to defaults (`null`, `false`, `-1`) only through the `Element.getAsString`, `getAsBoolean`, and `getAsInt` helpers. Getters documented as "Can return null" that return a primitive (e.g. `Event.getAtAsyncSuspension()` returns `boolean`) return `false`/`-1` instead.
- Object getters (e.g. `Event.getBreakpoint()`) read `type` (and sometimes `kind`) from the parent JSON without a null check, so a malformed object missing `type` throws an NPE.

## Side Effects
- Elements are read-only views with no setters, but `Element.getJson()` exposes the mutable backing `JsonObject`.
- `RPCError.unexpected(...)` builds a new error JSON object; it does not modify existing elements.
