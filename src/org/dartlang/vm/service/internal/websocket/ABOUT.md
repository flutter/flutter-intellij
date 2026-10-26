<!--* freshness: { reviewed: '2026-10-25' } *-->
# vmServiceDrivers WebSocket

## Overview
Provides a WebSocket client implementation wrapping the JDK's java.net.http.WebSocket for the Dart VM Service Drivers.

## Interface
- [`WebSocket.kt`](WebSocket.kt)
  - `class WebSocket(uri: URI)`
  - `@Volatile var WebSocket.eventHandler: WebSocketEventHandler?`
  - `fun WebSocket.connect()`
  - `fun WebSocket.send(text: String)`
  - `fun WebSocket.close()`
- [`WebSocketEventHandler.kt`](WebSocketEventHandler.kt)
  - `interface WebSocketEventHandler`
  - `fun WebSocketEventHandler.onOpen()`
  - `fun WebSocketEventHandler.onMessage(message: WebSocketMessage)`
  - `fun WebSocketEventHandler.onClose()`
  - `fun WebSocketEventHandler.onPing()`
  - `fun WebSocketEventHandler.onPong()`
- [`WebSocketException.kt`](WebSocketException.kt) - `class WebSocketException : Exception`
- [`WebSocketMessage.kt`](WebSocketMessage.kt) - `data class WebSocketMessage(val text: String)`

## Invariants
- Wraps the JDK's java.net.http.WebSocket.
- Shares a single HttpClient instance across all WebSocket instances.
- Manages underlying JdkWebSocket state atomically via AtomicReference.
- Synchronizes outgoing message sends using a dedicated lock.
- Enforces a 10-second timeout for connect, send, and close operations.
- Requests unbounded demand (`request(Long.MAX_VALUE)`) on open.
- Accumulates partial incoming text messages before delivering the complete payload to the handler.
- There is no separate error callback: a JDK listener `onError` clears state and is reported to the handler as `onClose()`.

## Side Effects
- Opens and closes network connections to the provided URI.
- Transmits text data over the active WebSocket connection.
- Invokes lifecycle and message callbacks on the registered WebSocketEventHandler.
- Interrupts the current thread if blocking connect/send/close operations are interrupted.
- Cancels incomplete CompletableFuture tasks upon timeout or failure.
- Throws WebSocketException on connection failure, send failure (including "WebSocket is not connected" when no socket exists), or close failure. `close()` silently returns if the socket is not connected.
