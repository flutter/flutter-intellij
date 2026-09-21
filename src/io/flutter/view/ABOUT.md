<!--* freshness: { reviewed: '2026-08-17' } *-->
# Flutter View

## Overview
Manages the Inspector View UI and DevTools embedding within the IDE using embedded browser instances.

## Interface
- `BrowserUrlProvider`
- `DevToolsUrlProvider`
- `EmbeddedBrowser`
- `EmbeddedBrowser.BrowserTab`
- `EmbeddedJcefBrowser`
- `EmbeddedTab`
- `FlutterViewMessages`
- `FlutterViewMessages.FlutterDebugNotifier`
- `FlutterViewMessages.FlutterDebugEvent`
- `InspectorView`
- `InspectorViewFactory`
- `ViewUtils`
- `WidgetPreviewUrlProvider`

## Invariants
- A single EmbeddedBrowser instance manages browser tabs across multiple tool windows for the project.
- EmbeddedBrowser instances automatically close all associated EmbeddedTabs when their bound Project closes.
- InspectorView tool window states are bound to workspace storage and update their content dynamically based on debug connection states.
- BrowserUrlProvider implementors encapsulate and track UI theme variations (e.g., widget preview themes) and VM service changes.

## Side Effects
- Registers ProjectManagerListeners to handle IDE lifecycle events like project closing (EmbeddedBrowser).
- Modifies tool window contents dynamically, adding, removing, or replacing panels, UI components, and browser tabs (EmbeddedBrowser, InspectorView, ViewUtils).
- Mutates the debug state of the FlutterApp and publishes synchronous events (FlutterDebugEvent) to the IDE's MessageBus (FlutterViewMessages.sendDebugActive).
- Initiates background processes and pooled threads for downloading, installing, or waiting for JxBrowser and DevTools components (InspectorView).
- Spawns standard external browsers via BrowserLauncher when embedded environments fail or are explicitly requested (InspectorView, EmbeddedBrowser).
