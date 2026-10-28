<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter Utils

## Overview
A collection of generic utility classes for file operations, IDE interactions, UI management, and background process handling.

## Interface
Static helper classes:
- [`AddToAppUtils`](AddToAppUtils.java)
- [`AndroidUtils`](AndroidUtils.java)
- [`AsyncUtils`](AsyncUtils.java)
- [`CollectionUtils`](CollectionUtils.java)
- [`CustomIconMaker`](CustomIconMaker.java) (with nested enum `CustomIconMaker.IconKind`)
- [`ElementIO`](ElementIO.java)
- [`FileUtils`](FileUtils.java)
- [`FlutterModuleUtils`](FlutterModuleUtils.java)
- [`GradleUtils`](GradleUtils.java) (the same file also holds a package-private `SdkConstants` holder)
- [`IconPreviewGenerator`](IconPreviewGenerator.java)
- [`JsonUtils`](JsonUtils.java)
- [`JxBrowserUtils`](JxBrowserUtils.java)
- [`OpenApiUtils`](OpenApiUtils.java)
- [`SystemUtils`](SystemUtils.java)
- [`UIUtils`](UIUtils.java)
- [`UrlUtils`](UrlUtils.java)

Stateful or instance-based types:
- [`AndroidLocationProvider`](AndroidLocationProvider.java)
- [`EventStream`](EventStream.java) - Observable value with listeners.
- [`FileWatch`](FileWatch.java) - Watches files for changes via VFS events.
- [`FlutterExternalSystemTaskNotificationListener`](FlutterExternalSystemTaskNotificationListener.java)
- [`LabelInput`](LabelInput.java)
- [`MostlySilentColoredProcessHandler`](MostlySilentColoredProcessHandler.java)
- [`ProgressHelper`](ProgressHelper.java)
- [`Refreshable`](Refreshable.java) - `Closeable` value refreshed in the background; nested `Refreshable.State` (BUSY/IDLE/CLOSED), `Refreshable.Callback`, `Refreshable.Request`.
- [`StdoutJsonParser`](StdoutJsonParser.java)
- [`StreamSubscription`](StreamSubscription.java) - `Disposable` subscription to an `EventStream`.
- [`TypedDataList`](TypedDataList.java) - Abstract base with nested `Int8List` … `Float64List` subclasses.
- [`ZoomLevelSelector`](ZoomLevelSelector.java)

Abstract adapters:
- [`ProcessAdapter`](ProcessAdapter.java)
- [`VmServiceListenerAdapter`](VmServiceListenerAdapter.java)

Package-private:
- [`EnableDartSupportForModule`](EnableDartSupportForModule.java) - `Runnable` used within this package; not public API.

## Invariants
- Most `*Utils` classes are static, stateless helpers; the stateful types listed above hold their own state and lifecycle (e.g. `Refreshable`, `StreamSubscription`, `FileWatch`).
- Heavy reliance on the IntelliJ Platform and OpenAPI.
- UI interactions must be scheduled on the Event Dispatch Thread (EDT).
- File and module operations depend on the IDE's internal read/write action locks.

## Side Effects
- Modifies IDE project and module state, such as enabling Dart/Flutter support.
- Reads from and writes to the local filesystem (e.g., FileUtils, IconPreviewGenerator).
- Spawns and manages external processes and consumes their outputs (e.g., ProcessAdapter, MostlySilentColoredProcessHandler, StdoutJsonParser).
- Sets up and manages asynchronous listeners and file watchers (e.g., FileWatch, VmServiceListenerAdapter).
- Updates UI components and indicators (e.g., UIUtils, ProgressHelper).
