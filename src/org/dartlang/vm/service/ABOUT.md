<!--* freshness: { reviewed: '2026-08-31' } *-->
# vmServiceDrivers Service

## Overview
Provides the main Dart VM Service client implementation, including the `VmService` class and related listeners for interacting with a running Dart VM via JSON-RPC.

## Interface
- `RemoteServiceCompleter` (interface) - Interface to be called when a service request completes successfully or with an error.
- `RemoteServiceRunner` (interface) - Interface used by VmService to register callbacks to services, handling incoming requests.
- `VmService` (class) - Main public class representing the Dart VM Service client. Provides methods for connecting to the VM (via inherited static `connect` methods), and a comprehensive set of typed RPC methods (e.g., `addBreakpoint`, `evaluate`, `getIsolate`, `resume`) to interact with the VM.
- `VmServiceBase` (class) - Base class for VmService providing connection management, WebSocket listeners, and JSON-RPC dispatching.
- `VmServiceListener` (interface) - Interface used to notify clients of VM connection state changes and incoming stream events.
- `consumer`: [./consumer/ABOUT.md](./consumer/ABOUT.md) - Consumer callback interfaces (e.g. AddBreakpointConsumer, InvokeConsumer).
- `element`: [./element/ABOUT.md](./element/ABOUT.md) - Data model elements (e.g. AllocationProfile, Breakpoint, Event, Instance).
- `internal`: [./internal/ABOUT.md](./internal/ABOUT.md) - Internal network sinks and WebSocket clients.
- `logging`: [./logging/ABOUT.md](./logging/ABOUT.md) - Logger interface and global Logging configuration.

## Invariants
- `VmService` is not thread-safe and must only be accessed from a single thread.
- A given Dart VM instance should only be accessed from a single instance of `VmService`.
- Calls to `VmService` should not be nested; specifically, calls to `VmService` should not be made from within any `Consumer` callback method.
- A thread-safe atomic counter (`nextId`) generates unique IDs for all outgoing JSON-RPC requests.
- A mapping (`consumerMap`) ensures that incoming JSON-RPC responses are accurately routed back to the initiating `Consumer`.

## Side Effects
- Establishes and maintains a persistent WebSocket connection to the Dart VM Service.
- Mutates the running state of the target Dart VM (e.g., adding/removing breakpoints, pausing/resuming execution, evaluating expressions).
- Notifies registered `VmServiceListener` instances asynchronously when events are received from subscribed VM streams.
- Executes `RemoteServiceRunner` callbacks when extension requests are received.
- Logs connection lifecycle events and errors to the globally configured Logging system.
