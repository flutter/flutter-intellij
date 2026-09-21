<!--* freshness: { reviewed: '2026-08-17' } *-->
# Flutter Widget Preview

## Overview
Provides a real-time widget preview UI tool window powered by the `flutter widget-preview` background command.

## Interface
- `class WidgetPreviewListener implements ProcessListener`
- `class WidgetPreviewPanel extends SimpleToolWindowPanel implements Disposable`
- `class WidgetPreviewToolWindowFactory implements ToolWindowFactory`

## Invariants
- The extracted widget preview URL will complete the urlFuture at most once.
- Widget Preview functionality requires a Flutter SDK version where sdk.getVersion().canUseWidgetPreview() evaluates to true.
- All UI mutations and browser load events are scheduled on the Event Dispatch Thread (EDT).

## Side Effects
- Spawns and manages the lifecycle of the external `flutter widget-preview` background process.
- Modifies IntelliJ tool window contents dynamically based on initialization state, errors, or browser availability.
- Opens either an embedded browser tab inside the IDE or launches an external browser.
- Subscribes to IntelliJ IDE theme change events via MessageBus to sync preview appearances.
- May show a UI file chooser dialog if a valid pub root cannot be resolved automatically.
