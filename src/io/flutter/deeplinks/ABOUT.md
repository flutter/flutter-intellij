<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter Deep Links

## Overview
Manages the integration and configuration of the Flutter Deep Links tool window and its corresponding DevTools page within the IDE. The tool window is registered in [`plugin.xml`](../../../../resources/META-INF/plugin.xml) (`id="Flutter Deep Links"`, `anchor="right"`, icon `FlutterIcons.DevToolsDeepLinks`).

## Interface
- [`class DeepLinksViewFactory`](DeepLinksViewFactory.java), extending [`AbstractDevToolsViewFactory`](../devtools/AbstractDevToolsViewFactory.java)
- `public static String DeepLinksViewFactory.TOOL_WINDOW_ID`
- `public static String DeepLinksViewFactory.DEVTOOLS_PAGE_ID`
- `boolean DeepLinksViewFactory.versionSupportsThisTool(FlutterSdkVersion flutterSdkVersion)`
- `String DeepLinksViewFactory.getToolWindowId()`
- `String DeepLinksViewFactory.getToolWindowTitle()`
- `DevToolsUrl DeepLinksViewFactory.getDevToolsUrl(Project project, FlutterSdkVersion flutterSdkVersion, DevToolsInstance instance)`

## Invariants
- `TOOL_WINDOW_ID` is initialized to "Flutter Deep Links" and `DEVTOOLS_PAGE_ID` to "deep-links". Both fields are `public static` but not `final`, so this is a convention, not enforced.
- `getToolWindowTitle()` strictly returns "Deep Links"
- Tool support is strictly dependent on `flutterSdkVersion.canUseDeepLinksTool()`
- `getDevToolsUrl` always configures the URL with embedded mode enabled, the deep-links page, and the `TOOL_WINDOW` IDE feature
- Behavior shared with other DevTools tool windows (always-applicable tool window, SDK warning labels, embedded browser loading, refresh title action) is inherited from `AbstractDevToolsViewFactory`; see [`devtools`](../devtools/ABOUT.md).
- Unlike the DevTools and DevTools Extensions factories, it has no `init()` hook or view service, so it does not receive VM service URI updates from debug sessions.

## Side Effects
- Instantiates a new DevToolsUrl via `DevToolsUrl.Builder` in `getDevToolsUrl`
