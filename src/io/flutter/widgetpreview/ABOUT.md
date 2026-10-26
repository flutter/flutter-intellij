<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter Widget Preview

## Overview
Provides a real-time widget preview UI tool window powered by the `flutter widget-preview` background command.

## Interface
- [`class WidgetPreviewListener implements ProcessListener`](WidgetPreviewListener.java) - Extracts the preview URL from `widget_preview.app.webLaunchUrl`/`widget_preview.started` JSON events, falling back to a `http://localhost:<port>` regex.
- [`class WidgetPreviewPanel extends SimpleToolWindowPanel implements Disposable`](WidgetPreviewPanel.java)
- [`class WidgetPreviewToolWindowFactory implements ToolWindowFactory`](WidgetPreviewToolWindowFactory.java) - Registered as the "Flutter Widget Preview" tool window in [`plugin.xml`](../../../../resources/META-INF/plugin.xml).

## Invariants
- The extracted widget preview URL will complete the urlFuture at most once.
- Widget Preview functionality requires a Flutter SDK version where sdk.getVersion().canUseWidgetPreview() evaluates to true. `isApplicableAsync` hides the tool window only when an SDK is found and known to be too old; with no SDK the window is shown and the panel reports the problem. The panel also reports an unknown/incomplete SDK version.
- `startWidgetPreview` runs on a pooled thread. Content swaps go through `ViewUtils.replacePanelLabel`/`invokeLater` on the EDT, but some Swing components (e.g. via `viewUtils.presentLabels`) are built on the pooled thread.

## Side Effects
- Spawns and manages the lifecycle of the external `flutter widget-preview` background process. Before starting it, the pooled thread waits up to 30s each for DTD (`DtdUtils.readyDtdService`) and DevTools (`DevToolsService`) and passes both URIs to `sdk.widgetPreview(...)`.
- Modifies IntelliJ tool window contents dynamically based on initialization state, errors, or browser availability.
- Opens the preview in an embedded browser tab. If no embedded browser is available, it shows an "Open in external browser" link that launches an external browser only when clicked. `openPanel` errors are printed to `System.out` and not shown in the UI.
- Subscribes to `EditorColorsManager.TOPIC` (editor color scheme changes, a temporary workaround per a TODO) on the project bus to sync preview appearance.
- If no pub root is found, shows a retry message with a "choose pub root" link; the file chooser dialog opens only when the user clicks it.
- `dispose()` tries to close `browserTabRef`, but that reference is never set, so no tab is closed there.
