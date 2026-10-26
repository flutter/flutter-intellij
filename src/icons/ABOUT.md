<!--* freshness: { reviewed: '2026-10-25' } *-->
# Icons

## Overview
Provides statically defined, centralized icons for the Flutter IntelliJ plugin, loaded from application resources. Everything is defined in [`FlutterIcons.java`](FlutterIcons.java) (package `icons`).

## Interface
- `FlutterIcons`
- `FlutterIcons.WidgetPreview`
- `FlutterIcons.DevToolsDeepLinks`
- `FlutterIcons.DevTools`
- `FlutterIcons.DevToolsExtensions`
- `FlutterIcons.DevToolsInspector`
- `FlutterIcons.PropertyEditor`
- `FlutterIcons.Flutter`
- `FlutterIcons.Flutter_2x`
- `FlutterIcons.Flutter_test`
- `FlutterIcons.RefreshItems`
- `FlutterIcons.Android`
- `FlutterIcons.IOS`
- `FlutterIcons.Mobile`
- `FlutterIcons.Desktop`
- `FlutterIcons.Web`
- `FlutterIcons.Dart_16`
- `FlutterIcons.HotReload`
- `FlutterIcons.HotRestart`
- `FlutterIcons.CustomClass`
- `FlutterIcons.CustomClassAbstract`
- `FlutterIcons.CustomMethod`
- `FlutterIcons.CustomMethodAbstract`
- `FlutterIcons.CustomInfo`
- `FlutterIcons.AttachDebugger`

## Invariants
- All icon fields are public, static, and final.
- Icons are loaded through the private `load(String)` helper, which calls `com.intellij.openapi.util.IconLoader.getIcon(path, FlutterIcons.class)`.
- Icon paths are absolute classpath resources under `/icons/` (files live in [`../../resources/icons/`](../../resources/icons/)). Exception: `DevToolsInspector` uses `"icons/expui/inspector.svg"` with no leading `/`.

## Side Effects
- Loads SVG and PNG icon resources from the classpath into memory during class initialization.
