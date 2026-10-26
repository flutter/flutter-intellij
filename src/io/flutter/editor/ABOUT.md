<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter Editor Features

## Overview
Provides editor-level enhancements: color and icon decorations on Dart completion items, gutter color previews and editing, icon previews, format-on-save, and pubspec/native-project notifications.

## Interface
- [`AndroidStudioColorPickerProvider`](AndroidStudioColorPickerProvider.java)
- [`ColorPickerProvider`](ColorPickerProvider.java)
- [`ExpressionParsingUtils`](ExpressionParsingUtils.java): parses `Color` literals (`Color(0x...)`, `fromARGB`, `fromRGBO`) for `FlutterColorProvider`.
- [`FlutterColorProvider`](FlutterColorProvider.java): an `ElementColorProvider` that supplies gutter color swatches and writes picked colors back to source.
- [`FlutterColors`](FlutterColors.java)
- [`FlutterCompletionContributor`](FlutterCompletionContributor.java): a `DartCompletionExtension` (registered as `completionExtension`). It adds no completions; it decorates Dart server suggestions on `Colors`, `CupertinoColors`, `Icons`, and `CupertinoIcons` with color/icon type icons.
- [`FlutterCupertinoColors`](FlutterCupertinoColors.java)
- [`FlutterCupertinoIcons`](FlutterCupertinoIcons.java)
- [`FlutterIconLineMarkerProvider`](FlutterIconLineMarkerProvider.java)
- [`FlutterMaterialIcons`](FlutterMaterialIcons.java)
- [`FlutterPubspecNotificationProvider`](FlutterPubspecNotificationProvider.java)
- [`FlutterReaderModeMatcher`](FlutterReaderModeMatcher.java)
- [`FlutterSaveActionsManager`](FlutterSaveActionsManager.java): registered as a `projectService`.
- [`IntellijColorPickerProvider`](IntellijColorPickerProvider.java)
- [`NativeEditorNotificationProvider`](NativeEditorNotificationProvider.java)

## Invariants
- Color and icon utilities map constant names to AWT colors and icons using resource properties files: [`material.properties`](../../../../resources/flutter/colors/material.properties) and [`cupertino.properties`](../../../../resources/flutter/colors/cupertino.properties) for colors, [`material.properties`](../../../../resources/flutter/icons/material.properties) and [`cupertino.properties`](../../../../resources/flutter/icons/cupertino.properties) for icons.
- The `ColorPickerProvider` implementations are registered on the `colorPickerProvider` extension point, but nothing in `src/` currently reads that extension point.
- FlutterSaveActionsManager acts only when format-on-save is enabled, on writable Dart files without PSI errors, in initialized projects with the Dart SDK enabled, and when the analysis server is ready. Organize imports runs only if organize-imports-on-save is also enabled.
- FlutterReaderModeMatcher returns no opinion for `READ_ONLY` mode. Otherwise it disables reader mode (returns false) only for files under a `PubRoot` in projects with Flutter modules.
- FlutterIconLineMarkerProvider checks icon classes against `KnownPaths`, a static map seeded with the Flutter SDK library directories for `Icons`, `IconData`, and `CupertinoIcons`. Previews are rendered with `io.flutter.utils.IconPreviewGenerator` from `.ttf` font files found via PSI resolution and directory scans. `fontPackage` specifications are not supported.
- FlutterPubspecNotificationProvider only shows for local pubspec files that declare Flutter, and only when a Flutter SDK is configured.

## Side Effects
- Modifies document content on save via DartAnalysisServerService to format code and organize imports (FlutterSaveActionsManager).
- Injects editor notification banners and gutter line markers into the IDE UI.
- Runs commands from editor notification panels: Pub get/upgrade/outdated and Flutter doctor (pubspec banner), and on macOS opening `ios`/`macos` project roots in Xcode (native banner, which also offers a "hide notification" action). The Android Studio option is disabled (issue #7103).
- The platform shows color picker popups for `FlutterColorProvider` swatches, and the provider rewrites source when a color is changed from the gutter.
