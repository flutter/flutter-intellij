<!--* freshness: { reviewed: '2026-08-17' } *-->
# Flutter Deep Links

## Overview
Manages the integration and configuration of the Flutter Deep Links tool window and its corresponding DevTools page within the IDE.

## Interface
- `class DeepLinksViewFactory`
- `public static String DeepLinksViewFactory.TOOL_WINDOW_ID`
- `public static String DeepLinksViewFactory.DEVTOOLS_PAGE_ID`
- `boolean DeepLinksViewFactory.versionSupportsThisTool(FlutterSdkVersion flutterSdkVersion)`
- `String DeepLinksViewFactory.getToolWindowId()`
- `String DeepLinksViewFactory.getToolWindowTitle()`
- `DevToolsUrl DeepLinksViewFactory.getDevToolsUrl(Project project, FlutterSdkVersion flutterSdkVersion, DevToolsInstance instance)`

## Invariants
- `TOOL_WINDOW_ID` is strictly equal to "Flutter Deep Links"
- `DEVTOOLS_PAGE_ID` is strictly equal to "deep-links"
- `getToolWindowTitle()` strictly returns "Deep Links"
- Tool support is strictly dependent on `flutterSdkVersion.canUseDeepLinksTool()`
- `getDevToolsUrl` always configures the URL with embedded mode enabled, the deep-links page, and the `TOOL_WINDOW` IDE feature

## Side Effects
- Instantiates a new DevToolsUrl via `DevToolsUrl.Builder` in `getDevToolsUrl`
