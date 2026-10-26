<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter Logging

## Overview
Handles parsing, formatting, and logging of structured diagnostic events and errors from Flutter applications. It also owns the plugin's own log file setup.

Key files:
- [FlutterConsoleLogManager.java](FlutterConsoleLogManager.java): renders `Flutter.Error` structured errors and `dart:developer` logging events into a run console.
- [DiagnosticsNode.java](DiagnosticsNode.java): wrapper around a Flutter diagnostics JSON node. [DiagnosticLevel.java](DiagnosticLevel.java), [DiagnosticsTreeStyle.java](DiagnosticsTreeStyle.java) and [InspectorInstanceRef.java](InspectorInstanceRef.java) support it.
- [FlutterConsoleLogFolding.java](FlutterConsoleLogFolding.java): console folding extension, registered as `console.folding` in [plugin.xml](../../../../resources/META-INF/plugin.xml). It folds console lines that start with `"...  "`.
- [FlutterErrorHelper.java](FlutterErrorHelper.java): `getAnalyticsId(String)` normalizes an error summary (lower-cased, parenthesized text and assertion paths removed, numbers replaced with `xxx`) into a stable analytics ID.
- [PluginLogger.java](PluginLogger.java): configures the `io.flutter` root logger and creates per-class loggers.

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
- FlutterConsoleLogManager processes logs and errors sequentially on a background `QueueProcessor`. The queue is a static instance shared by all manager instances.
- Structured Flutter errors are queued only when `FlutterSettings.isShowStructuredErrors()` is enabled.
- `PluginLogger.initLogger` attaches at most one `FileHandler` to the `io.flutter` root logger. The `isInitialized` check runs outside the lock and the field is not volatile, so the guard is best-effort. If a `FileHandler` already exists on the `io.flutter` or `com.jetbrains.lang.dart` logger, it is reused, so the log file can be shared with the Dart plugin.
- DiagnosticsNode wraps a JSON object. `getChildren()` parses children synchronously on first call and returns them wrapped in `CompletableFuture.completedFuture`. `getInlineProperties()` caches parsed properties in a plain `ArrayList`.
- DiagnosticsNode constructors ignore their `parent` and `app` arguments (use `setParent` instead). `equals` compares the Dart diagnostic reference, but `hashCode` is not overridden.
- InspectorInstanceRef objects are uniquely identified by their string 'id', enforcing equality based on the ID.

## Side Effects
- `FlutterConsoleLogManager.initConsolePreferences` enables console soft wrapping in `EditorSettingsExternalizable` once per IDE install. The `io.flutter.console.preferencesSet` `PropertiesComponent` flag prevents it from running again.
- FlutterConsoleLogManager queues text to be rendered asynchronously in the IDE's ConsoleView. It raises IDE notifications (through the `deeplink` notification group) only for `DevToolsDeepLinkProperty` nodes.
- `PluginLogger.initLogger` creates or appends to `dash.log` in the IDE log directory, but only when no existing handler is found. The file rotates at 10 MB across 5 files.
- `PluginLogger.updateLogLevel` globally sets the `io.flutter` root logger level (ALL when verbose logging is on, otherwise INFO). It skips the change for the Jmix logger wrapper and swallows `setLevel` failures from third-party wrappers.
