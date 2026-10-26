<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter Tool Window

## Overview
Listens to tool window state changes and updates badged icons indicating running or debugging Flutter app states.

Callers: [`InspectorView`](../view/InspectorView.java) creates the listener; [`LaunchState`](../run/LaunchState.java) calls the badge updater when an app launches.

## Interface
- [`class InspectorViewToolWindowManagerListener implements ToolWindowManagerListener`](InspectorViewToolWindowManagerListener.java)
- `InspectorViewToolWindowManagerListener.InspectorViewToolWindowManagerListener(Project project, ToolWindow toolWindow)`
- `void InspectorViewToolWindowManagerListener.updateOnWindowOpen(Runnable onWindowOpen)`
- `void InspectorViewToolWindowManagerListener.updateOnWindowFirstVisible(Runnable onWindowFirstVisible)`
- `void InspectorViewToolWindowManagerListener.stateChanged(@NotNull ToolWindowManager toolWindowManager)`
- [`class ToolWindowBadgeUpdater`](ToolWindowBadgeUpdater.java)
- `static void ToolWindowBadgeUpdater.updateBadgedIcon(FlutterApp app, Project project)`

## Invariants
- `InspectorViewToolWindowManagerListener` tracks whether the inspector window stripe button is visible based on tool window state.
- Each callback registered via `updateOnWindowFirstVisible` runs at most once (the reference is cleared after it runs). Calling `updateOnWindowFirstVisible` again re-arms it; `InspectorView` does this for each debug session while the window is hidden.
- `ToolWindowBadgeUpdater` applies an icon badge to the RUN tool window for `RunMode.RUN` and to the DEBUG tool window for `RunMode.DEBUG`. Other modes (e.g. PROFILE) get no badge, and nothing in this package removes a badge when the app stops.

## Side Effects
- `InspectorViewToolWindowManagerListener` subscribes to `ToolWindowManagerListener.TOPIC` on the project's message bus during construction, using `connect()` with no parent disposable, so the subscription lives as long as the project's message bus.
- `InspectorViewToolWindowManagerListener` executes the provided Runnable callbacks (`onWindowOpen` and `onWindowFirstVisible`) in response to tool window visibility state changes.
- `ToolWindowBadgeUpdater` schedules asynchronous UI updates via `ToolWindowManager.invokeLater()` to modify the global tool window icons with a LayeredIcon.
