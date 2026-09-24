<!--* freshness: { reviewed: '2026-08-17' } *-->
# Flutter Utils

## Overview
A collection of generic utility classes for file operations, IDE interactions, UI management, and background process handling.

## Interface
- `AddToAppUtils`
- `AndroidLocationProvider`
- `AndroidUtils`
- `AsyncUtils`
- `CollectionUtils`
- `CustomIconMaker`
- `ElementIO`
- `EnableDartSupportForModule`
- `EventStream`
- `FileUtils`
- `FileWatch`
- `FlutterExternalSystemTaskNotificationListener`
- `FlutterModuleUtils`
- `GradleUtils`
- `IconPreviewGenerator`
- `JsonUtils`
- `JxBrowserUtils`
- `LabelInput`
- `MostlySilentColoredProcessHandler`
- `OpenApiUtils`
- `ProcessAdapter`
- `ProgressHelper`
- `Refreshable`
- `StdoutJsonParser`
- `StreamSubscription`
- `SystemUtils`
- `TypedDataList`
- `UIUtils`
- `UrlUtils`
- `VmServiceListenerAdapter`
- `ZoomLevelSelector`

## Invariants
- Utility classes consist largely of static, stateless methods.
- Heavy reliance on the IntelliJ Platform and OpenAPI.
- UI interactions must be scheduled on the Event Dispatch Thread (EDT).
- File and module operations depend on the IDE's internal read/write action locks.

## Side Effects
- Modifies IDE project and module state, such as enabling Dart/Flutter support.
- Reads from and writes to the local filesystem (e.g., FileUtils, IconPreviewGenerator).
- Spawns and manages external processes and consumes their outputs (e.g., ProcessAdapter, MostlySilentColoredProcessHandler, StdoutJsonParser).
- Sets up and manages asynchronous listeners and file watchers (e.g., FileWatch, VmServiceListenerAdapter).
- Updates UI components and indicators (e.g., UIUtils, ProgressHelper).
