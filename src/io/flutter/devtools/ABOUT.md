<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter DevTools

## Overview
Configures and embeds the Flutter DevTools suite as IDE tool windows. The tool windows are registered in [`plugin.xml`](../../../../resources/META-INF/plugin.xml).

## Interface
- [`AbstractDevToolsViewFactory`](AbstractDevToolsViewFactory.java): base `ToolWindowFactory`. Subclasses here are `DevToolsExtensionsViewFactory` and `RemainingDevToolsViewFactory`. Subclasses elsewhere are [`DeepLinksViewFactory`](../deeplinks/DeepLinksViewFactory.java) and [`PropertyEditorViewFactory`](../propertyeditor/PropertyEditorViewFactory.java).
- [`DevToolsExtensionsViewFactory`](DevToolsExtensionsViewFactory.java): "Flutter DevTools Extensions" tool window (`hide=all-except-extensions`).
- [`DevToolsExtensionsViewService`](DevToolsExtensionsViewService.java)
- [`DevToolsIdeFeature`](DevToolsIdeFeature.java): `ON_DEBUG_AUTOMATIC`, `RUN_CONSOLE`, `TOOL_WINDOW`, `TOOL_WINDOW_RELOAD`; sent as the `ideFeature` URL parameter.
- [`DevToolsUrl`](DevToolsUrl.java)
- [`DevToolsUtils`](DevToolsUtils.java): `findWidgetId`, `registerDevToolsVmServiceListener`, and the theme helpers `getColorHexCode`, `getIsBackgroundBright`, `getFontSize` (also used by `FlutterInitializer` for DTD `themeChanged` events).
- [`DevToolsViewService`](DevToolsViewService.java)
- [`RemainingDevToolsViewFactory`](RemainingDevToolsViewFactory.java): "Flutter DevTools" tool window (`hide=home,inspector,deep-links,extensions,debugger`).
- [`RemainingDevToolsViewService`](RemainingDevToolsViewService.java)

## Invariants
- Tool window applicability (isApplicableAsync) always returns true as a workaround for an existing issue.
- Tool window content creation strictly requires a valid, supported Flutter SDK version; otherwise, warning labels are presented.
- The `*ViewService` subclasses are project-level services (`@Service(Service.Level.PROJECT)`). If a vmServiceUri is updated in DevToolsViewService before the embedded browser is initialized, only the most recent URI is cached and applied once the browser is set.
- Both the Extensions and Remaining factories return "Flutter DevTools" from `getToolWindowTitle()`, so the Extensions window isn't distinguished by title.
- DevTools URLs always include `ide`, `dashTool=intellij-plugins`, and `dashIdeName`. Optional parameters are `backgroundColor`, `theme`, `ideFeature`, `uri` (VM service), and `inspectorRef` (widget ID). Color and theme are captured only when `embed` is true.
- Embedded URLs use embedMode=many plus the hide parameter for multi-embed supported SDKs (or embedMode=one if hide is not provided), or fall back to embed=true for older SDKs.
- DevToolsUrl.Builder ensures non-null defaults for devToolsUtils, flutterSdkUtil, and defaults embed to false.

## Side Effects
- AbstractDevToolsViewFactory registers a ToolWindowManagerListener on the project message bus. When the tool window is shown, it loads DevTools only if no load has succeeded yet.
- The static `init(Project)` of DevToolsExtensionsViewFactory and RemainingDevToolsViewFactory, called from `FlutterInitializer`, subscribes to FLUTTER_DEBUG_TOPIC. On debug events it pushes the app's `getConnector().getBrowserUrl()` to the matching view service.
- DevToolsUtils.registerDevToolsVmServiceListener adds a VM service listener that triggers file navigation in the IDE upon receiving 'ToolEvent' navigate messages.
- DevToolsUrl.maybeUpdateColor mutates colorHexCode and isBright state if the IDE's underlying editor colors change.
- Loads DevTools in an EmbeddedBrowser and adds a RefreshToolWindowAction to the tool window title actions.
