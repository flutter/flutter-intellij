<!--* freshness: { reviewed: '2026-08-17' } *-->
# Flutter Inspections

## Overview
Provides editor inspections and notifications, particularly around SDK configuration requirements for Flutter files.

## Interface
- `class SdkConfigurationNotificationProvider`
- `SdkConfigurationNotificationProvider(Project project)`
- `Function<? super FileEditor, ? extends JComponent> collectNotificationData(Project project, VirtualFile file)`

## Invariants
- Notifications are restricted to Dart files.
- Notifications are restricted to files belonging to a Flutter module.
- The SDK configuration notification is only provided when no Flutter SDK is found for the project.

## Side Effects
- Creates and configures an `EditorNotificationPanel` warning the user about a missing Flutter SDK.
- Hides the notification panel when dismissed.
- Opens the Flutter settings dialog when the user clicks 'Open Flutter settings'.
