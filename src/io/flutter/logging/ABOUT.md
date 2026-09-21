<!--* freshness: { reviewed: '2026-08-17' } *-->
# Flutter Logging

## Overview
Handles parsing, formatting, and logging of structured diagnostic events and errors from Flutter applications.

## Interface
- `DiagnosticLevel`
- `DiagnosticsNode`
- `DiagnosticsTreeStyle`
- `FlutterConsoleLogFolding`
- `FlutterConsoleLogManager`
- `FlutterErrorHelper`
- `InspectorInstanceRef`
- `PluginLogger`

## Invariants
- FlutterConsoleLogManager processes logs and errors sequentially via a background QueueProcessor.
- PluginLogger ensures exactly one file handler is registered for the 'io.flutter' root logger in a thread-safe manner.
- DiagnosticsNode wraps a JSON object, lazily parsing child nodes and properties into DiagnosticsNode instances via CompletableFuture.
- InspectorInstanceRef objects are uniquely identified by their string 'id', enforcing equality based on the ID.

## Side Effects
- FlutterConsoleLogManager.initConsolePreferences sets global IDE properties and modifies EditorSettingsExternalizable to enable console soft wrapping.
- FlutterConsoleLogManager queues text to be rendered asynchronously in the IDE's ConsoleView and can trigger OS-level Notifications.
- PluginLogger.initLogger performs filesystem I/O to create/append to the 'dash.log' file in the plugin log directory.
- PluginLogger.updateLogLevel globally mutates the logging level of the 'io.flutter' root logger.
