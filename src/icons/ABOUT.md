<!--* freshness: { reviewed: '2026-08-17' } *-->
# Icons

## Overview
Provides statically defined, centralized icons for the Flutter IntelliJ plugin, loaded from application resources.

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
- Icons are loaded via `com.intellij.openapi.util.IconLoader` with the `FlutterIcons` class as the base for resolution.

## Side Effects
- Loads SVG and PNG icon resources from the classpath into memory during class initialization.
