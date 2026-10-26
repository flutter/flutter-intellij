<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter View

## Overview
Manages the Inspector View UI and DevTools embedding within the IDE using embedded browser instances.

## Interface
- [`BrowserUrlProvider`](BrowserUrlProvider.java)
- [`DevToolsUrlProvider`](DevToolsUrlProvider.java)
- [`EmbeddedBrowser`](EmbeddedBrowser.java) - Abstract base. Besides `openPanel`, exposes `updatePanelToWidget`, `updateVmServiceUri` and `refresh(toolWindowId)`.
- `EmbeddedBrowser.BrowserTab`
- [`EmbeddedJcefBrowser`](EmbeddedJcefBrowser.java) - JCEF implementation (project service). The same file holds the package-private `EmbeddedJcefBrowserTab`.
- [`EmbeddedTab`](EmbeddedTab.java)
- [`FlutterViewMessages`](FlutterViewMessages.java)
- `FlutterViewMessages.FlutterDebugNotifier`
- `FlutterViewMessages.FlutterDebugEvent`
- [`InspectorView`](InspectorView.java) - Project service that fills the "Flutter Inspector" tool window.
- [`InspectorViewFactory`](InspectorViewFactory.java) - `ToolWindowFactory` registered in [`plugin.xml`](../../../../resources/META-INF/plugin.xml); `init` is called from [`FlutterInitializer`](../FlutterInitializer.java).
- [`ViewUtils`](ViewUtils.java)
- [`WidgetPreviewUrlProvider`](WidgetPreviewUrlProvider.java)

The other `EmbeddedBrowser` implementation is [`EmbeddedJxBrowser`](../jxbrowser/EmbeddedJxBrowser.java). `FlutterUtils.embeddedBrowser(project)` picks JCEF or JxBrowser based on `FlutterSettings.isEnableJcefBrowser()`.

## Invariants
- Each `EmbeddedBrowser` implementation is a project service, so there is one instance per implementation per project, managing tabs across multiple tool windows.
- EmbeddedBrowser instances close all associated EmbeddedTabs when their bound Project closes. For JCEF, `EmbeddedJcefBrowserTab.close()` (and `matchIdeZoom()`) are no-ops, so the `JBCefBrowser` is not explicitly disposed.
- InspectorView updates its content dynamically based on debug connection states. It carries a `@State(... $WORKSPACE_FILE$)` annotation but does not implement `PersistentStateComponent`, so nothing is actually persisted.
- `InspectorViewFactory.init` activates the window if the `flutter.view.tool.window.visible` property is set, but nothing in `src/` writes that property.
- BrowserUrlProvider implementors encapsulate UI theme variations (e.g., widget preview themes). `DevToolsUrlProvider` also tracks VM service URI and widget id changes; `WidgetPreviewUrlProvider` ignores both (`setVmServiceUri` returns `false`, `setWidgetId` is a no-op).

## Side Effects
- Registers ProjectManagerListeners to handle IDE lifecycle events like project closing (EmbeddedBrowser).
- Modifies tool window contents dynamically, adding, removing, or replacing panels, UI components, and browser tabs (EmbeddedBrowser, InspectorView, ViewUtils). InspectorView also sets a `RefreshToolWindowAction` title action.
- `FlutterViewMessages.sendDebugActive` creates a `VMServiceManager`, registers it with `Disposer` under the `VmServiceWrapper`, calls `app.setVmServices`, and publishes a synchronous `FlutterDebugEvent` on the IDE's MessageBus.
- InspectorView calls `DevToolsUtils.registerDevToolsVmServiceListener` for the app.
- Initiates background processes and pooled threads for downloading, installing, or waiting for JxBrowser and DevTools components (InspectorView). When JxBrowser is NOT_INSTALLED or INSTALLATION_SKIPPED, InspectorView triggers `JxBrowserManager.setUp`.
- Spawns standard external browsers via BrowserLauncher when embedded environments fail or are explicitly requested (InspectorView, EmbeddedBrowser).
