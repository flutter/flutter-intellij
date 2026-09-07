<!--* freshness: { reviewed: '2026-08-17' } *-->
# vmServiceDrivers Internal

## Overview
Provides internal network sinks, queues, and constants for managing JSON-RPC requests and responses to the Dart VM Service over WebSockets.

## Interface
- `class BlockingRequestSink`
- `class ErrorRequestSink`
- `interface RequestSink`
- `interface ResponseSink`
- `interface VmServiceConst`
- `class WebSocketRequestSink`
- `websocket`: [./websocket/ABOUT.md](./websocket/ABOUT.md) - Provides a WebSocket client implementation wrapping the JDK's java.net.http.WebSocket.

## Invariants
- `BlockingRequestSink` synchronizes all access to its internal request queue.
- `ErrorRequestSink` throws `IllegalArgumentException` if its constructor arguments (responseSink, code, message) are null.
- `WebSocketRequestSink` sets its webSocket reference to null upon closing, preventing further operations and safely dropping subsequent requests.

## Side Effects
- `BlockingRequestSink` buffers requests in memory, which are flushed as side effects when converted to an error or passthrough sink.
- `ErrorRequestSink` converts incoming requests into JSON error responses and sends them to its configured `ResponseSink`.
- `WebSocketRequestSink` serializes JSON objects to strings and transmits them over a network WebSocket.
- `ErrorRequestSink` and `WebSocketRequestSink` both log errors and information (like dropped or sent messages) using a global `Logging` facility.
