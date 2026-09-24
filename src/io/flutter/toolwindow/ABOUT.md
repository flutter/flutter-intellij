<!--* freshness: { reviewed: '2026-08-17' } *-->
# Flutter Tool Window

## Overview
Listens to tool window state changes and updates badged icons indicating running or debugging Flutter app states.

## Interface
- `class InspectorViewToolWindowManagerListener implements ToolWindowManagerListener`
- `InspectorViewToolWindowManagerListener.InspectorViewToolWindowManagerListener(Project project, ToolWindow toolWindow)`
- `void InspectorViewToolWindowManagerListener.updateOnWindowOpen(Runnable onWindowOpen)`
- `void InspectorViewToolWindowManagerListener.updateOnWindowFirstVisible(Runnable onWindowFirstVisible)`
- `void InspectorViewToolWindowManagerListener.stateChanged(@NotNull ToolWindowManager toolWindowManager)`
- `class ToolWindowBadgeUpdater`
- `static void ToolWindowBadgeUpdater.updateBadgedIcon(FlutterApp app, Project project)`

## Invariants
- `InspectorViewToolWindowManagerListener` tracks whether the inspector window stripe button is visible based on tool window state.
- `InspectorViewToolWindowManagerListener` guarantees that the `onWindowFirstVisible` callback is executed at most once, clearing the callback reference after execution.
- `ToolWindowBadgeUpdater` applies an icon badge to either the RUN or DEBUG tool window strictly depending on the `RunMode` of the provided `FlutterApp`.

## Side Effects
- `InspectorViewToolWindowManagerListener` subscribes to `ToolWindowManagerListener.TOPIC` on the project's message bus during construction.
- `InspectorViewToolWindowManagerListener` executes the provided Runnable callbacks (`onWindowOpen` and `onWindowFirstVisible`) in response to tool window visibility state changes.
- `ToolWindowBadgeUpdater` schedules asynchronous UI updates via `ToolWindowManager.invokeLater()` to modify the global tool window icons with a LayeredIcon.
