<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter Inspections

## Overview
Provides editor notifications about SDK configuration requirements for Flutter files. Despite the package name, it contains no code inspections (`LocalInspectionTool`). Its only class, [SdkConfigurationNotificationProvider](SdkConfigurationNotificationProvider.java), implements `EditorNotificationProvider` and is registered as an `editorNotificationProvider` in [plugin.xml](../../../../resources/META-INF/plugin.xml).

## Interface
- `class SdkConfigurationNotificationProvider implements EditorNotificationProvider`
- `SdkConfigurationNotificationProvider(Project project)`
- `Function<? super FileEditor, ? extends JComponent> collectNotificationData(Project project, VirtualFile file)`

## Invariants
- Notifications are restricted to Dart files: the file type must be Dart and its PSI language must be `DartLanguage`.
- Notifications are restricted to files belonging to a Flutter module.
- The SDK configuration notification is only provided when no Flutter SDK is found for the project.
- `collectNotificationData` uses its `project` parameter. The `project` stored by the constructor is unused, and the `FileEditor` passed to the returned function is ignored when the panel is built.

## Side Effects
- Creates and configures an `EditorNotificationPanel` warning the user about a missing Flutter SDK.
- Hides the notification panel when dismissed.
- Opens the Flutter settings dialog when the user clicks 'Open Flutter settings'.
