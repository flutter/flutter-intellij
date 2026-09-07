<!--* freshness: { reviewed: '2026-08-17' } *-->
# Flutter Editor Features

## Overview
Provides editor-level enhancements such as code completion, color pickers, icon previews, and pubspec notifications.

## Interface
- `ActiveEditorsOutlineService`
- `AndroidStudioColorPickerProvider`
- `ColorPickerProvider`
- `ExpressionParsingUtils`
- `FlutterColorProvider`
- `FlutterColors`
- `FlutterCompletionContributor`
- `FlutterCupertinoColors`
- `FlutterCupertinoIcons`
- `FlutterIconLineMarkerProvider`
- `FlutterMaterialIcons`
- `FlutterPubspecNotificationProvider`
- `FlutterReaderModeMatcher`
- `FlutterSaveActionsManager`
- `IntellijColorPickerProvider`
- `NativeEditorNotificationProvider`

## Invariants
- ActiveEditorsOutlineService maintains synchronization between active Dart editor tabs and FlutterOutline data.
- Color utilities and Icon utilities map constant names to AWT colors and icons using resource properties files.
- FlutterSaveActionsManager strictly targets writable Dart files inside initialized projects with the Dart SDK enabled.
- FlutterReaderModeMatcher disables the default IDE reader mode for files within Flutter packages.
- FlutterIconLineMarkerProvider caches known icon paths and relies on static analysis to resolve font packages.

## Side Effects
- Modifies document content on save via DartAnalysisServerService to format code and organize imports (FlutterSaveActionsManager).
- Subscribes to DartAnalysisServer and FileEditorManager events to manage outline listeners (ActiveEditorsOutlineService).
- Injects editor notification banners and gutter line markers into the IDE UI.
- Executes external commands (Pub get/upgrade/outdated, and opening Xcode/Android Studio) triggered via editor notification panel actions.
- Spawns color picker UI popups and directly modifies source code when colors are updated via the editor gutter.
