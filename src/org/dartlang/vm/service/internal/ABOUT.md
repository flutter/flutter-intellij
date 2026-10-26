<!--* freshness: { reviewed: '2026-10-25' } *-->
# vmServiceDrivers Internal

## Overview
Provides internal network sinks, queues, and constants for managing JSON-RPC requests and responses to the Dart VM Service over WebSockets.

## Interface
- [`RequestSink`](RequestSink.java) (interface) - `add(JsonObject)` and `close()`.
- [`WebSocketRequestSink`](WebSocketRequestSink.java) - The `RequestSink` used by `VmServiceBase`; sends requests over a [`WebSocket`](websocket/WebSocket.kt).
- [`VmServiceConst`](VmServiceConst.java) (interface) - JSON-RPC key strings (`id`, `method`, `params`, `result`, `error`, `streamId`, `jsonrpc`/`"2.0"`, ...) and `int` error codes `PARSE_ERROR`, `INVALID_REQUEST`, `METHOD_NOT_FOUND`, `INVALID_PARAMS`, `SERVER_ERROR`.
- [`BlockingRequestSink`](BlockingRequestSink.java), [`ErrorRequestSink`](ErrorRequestSink.java), [`ResponseSink`](ResponseSink.java) - Legacy helpers that are not referenced outside this directory.
- `websocket`: [./websocket/ABOUT.md](./websocket/ABOUT.md) - Provides a WebSocket client implementation wrapping the JDK's java.net.http.WebSocket.

## Invariants
- `BlockingRequestSink` synchronizes all access to its internal request queue. `toErrorSink`/`toPassthroughSink` replay the queue but never clear it, and `close()` closes the base sink without flushing or discarding queued requests.
- `ErrorRequestSink` throws `IllegalArgumentException` if its constructor arguments (responseSink, code, message) are null. Its `code` is a `String`, although the `VmServiceConst` error codes are `int`.
- `WebSocketRequestSink` sets its webSocket reference to null upon closing, preventing further operations and safely dropping subsequent requests.

## Side Effects
- `BlockingRequestSink` buffers requests in memory, which are flushed as side effects when converted to an error or passthrough sink.
- `ErrorRequestSink` converts incoming requests into JSON error responses and sends them to its configured `ResponseSink`.
- `WebSocketRequestSink` serializes JSON objects to strings and transmits them over a network WebSocket. It logs each request as "Sent:" or "Dropped:" via `Logging`, and catches `WebSocketException` from `send`/`close` and logs it rather than propagating.
- `ErrorRequestSink` only logs errors (exceptions while building or sending a response); it does not log information.
