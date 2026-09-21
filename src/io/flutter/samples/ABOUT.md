<!--* freshness: { reviewed: '2026-08-17' } *-->
# Flutter Samples

## Overview
Provides IDE integration for interactive Flutter samples (dartpad references) within the SDK's documentation.

## Interface
- `class DartDocumentUtils`
- `DartDocumentUtils.getDartdocFor(Document, DartComponent): List<String>`
- `class FlutterSample`
- `FlutterSample.getLibraryName(): String`
- `FlutterSample.getClassName(): String`
- `FlutterSample.getDisplayName(): String`
- `FlutterSample.getHostedDocsUrl(): String`
- `FlutterSample.toString(): String`
- `class FlutterSampleNotificationProvider implements EditorNotificationProvider`
- `FlutterSampleNotificationProvider(Project)`
- `FlutterSampleNotificationProvider.collectNotificationData(Project, VirtualFile): Function`
- `FlutterSampleNotificationProvider.containsDartdocFlutterSample(List<String>): boolean`

## Invariants
- FlutterSample properties (libraryName and className) are non-null and immutable.
- Editor notifications for samples are restricted to files located within the Flutter SDK home path.
- Flutter samples are identified by searching dartdoc comments for the regex pattern matching `{@tool dartpad ...}`.
- Only public Dart classes (names not starting with `_`) are evaluated for dartpad sample references.
- Dartdoc text extraction relies on reverse scanning document lines from the component's starting offset, ignoring blank lines and annotations.

## Side Effects
- Modifies the editor UI by displaying an EditorNotificationPanel (FlutterSampleActionsPanel) with clickable links when applicable Flutter SDK files are opened.
- Opens the system's default web browser navigating to the generated flutter.dev documentation URL upon clicking a sample link.
