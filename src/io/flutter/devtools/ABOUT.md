<!--* freshness: { reviewed: '2026-08-17' } *-->
# Flutter DevTools

## Overview
Configures and embeds the Flutter DevTools suite as IDE tool windows.

## Interface
- `AbstractDevToolsViewFactory`
- `DevToolsExtensionsViewFactory`
- `DevToolsExtensionsViewService`
- `DevToolsIdeFeature`
- `DevToolsUrl`
- `DevToolsUtils`
- `DevToolsViewService`
- `RemainingDevToolsViewFactory`
- `RemainingDevToolsViewService`

## Invariants
- Tool window applicability (isApplicableAsync) always returns true as a workaround for an existing issue.
- Tool window content creation strictly requires a valid, supported Flutter SDK version; otherwise, warning labels are presented.
- If a vmServiceUri is updated in DevToolsViewService before the embedded browser is initialized, it is cached and applied later once the browser is set.
- DevTools URLs configure embedMode=many and append hide parameters for multi-embed supported SDKs (or embedMode=one if hide is not provided), or fallback to embed=true for older SDKs.
- DevToolsUrl.Builder ensures non-null defaults for devToolsUtils, flutterSdkUtil, and defaults embed to false.

## Side Effects
- AbstractDevToolsViewFactory registers a ToolWindowManagerListener on the project message bus to reload DevTools when the tool window is shown.
- DevToolsExtensionsViewFactory and RemainingDevToolsViewFactory initialization subscribe to FLUTTER_DEBUG_TOPIC on the message bus to update the VM service URI when debug events occur.
- DevToolsUtils.registerDevToolsVmServiceListener adds a VM service listener that triggers file navigation in the IDE upon receiving 'ToolEvent' navigate messages.
- DevToolsUrl.maybeUpdateColor mutates colorHexCode and isBright state if the IDE's underlying editor colors change.
- Loads DevTools in an EmbeddedBrowser and adds a RefreshToolWindowAction to the tool window title actions.
