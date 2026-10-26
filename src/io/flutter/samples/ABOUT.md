<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter Samples

## Overview
Provides IDE integration for interactive Flutter samples (dartpad references) within the SDK's documentation.

## Interface
- `class DartDocumentUtils` ([`DartDocumentUtils.java`](DartDocumentUtils.java))
- `DartDocumentUtils.getDartdocFor(Document, DartComponent): List<String>`
- `class FlutterSample` ([`FlutterSample.java`](FlutterSample.java)); package-private constructor `FlutterSample(String libraryName, String className)`
- `FlutterSample.getLibraryName(): String`
- `FlutterSample.getClassName(): String`
- `FlutterSample.getDisplayName(): String`
- `FlutterSample.getHostedDocsUrl(): String`: `https://api.flutter.dev/flutter/<library>/<Class>-class.html`
- `FlutterSample.toString(): String`
- `class FlutterSampleNotificationProvider implements EditorNotificationProvider` ([`FlutterSampleNotificationProvider.java`](FlutterSampleNotificationProvider.java))
- `FlutterSampleNotificationProvider(Project)`
- `FlutterSampleNotificationProvider.collectNotificationData(Project, VirtualFile): Function<? super FileEditor, ? extends JComponent>`
- `@VisibleForTesting public static FlutterSampleNotificationProvider.containsDartdocFlutterSample(List<String>): boolean`
- `class FlutterSampleActionsPanel extends EditorNotificationPanel` (package-private, in `FlutterSampleNotificationProvider.java`): the panel with one link per sample.

## Invariants
- FlutterSample properties (libraryName and className) are non-null and immutable.
- Editor notifications for samples are restricted to files under `<flutter sdk home>/packages/flutter/lib/src/`.
- A FlutterSample is created only when the file path has a library subdirectory under `lib/src/`; `libraryName` is that first path segment (e.g. `material`).
- Only top-level `DartClass` children of the file are scanned, and only public classes (names not starting with `_`).
- Flutter samples are identified by searching dartdoc comments for the regex pattern matching `{@tool dartpad ...}`.
- Dartdoc text extraction scans document lines upward from the line of the class's name identifier (`getTextOffset()`), skipping blank lines and annotations. The internal `getLine(document, line)` helper reads document line `line - 1`, so it can throw `IndexOutOfBoundsException` near the top of a file; the provider catches and ignores it.

## Side Effects
- Modifies the editor UI by displaying an EditorNotificationPanel (FlutterSampleActionsPanel) with clickable links when applicable Flutter SDK files are opened.
- Opens the system's default web browser at the sample's api.flutter.dev class page (`getHostedDocsUrl()`) upon clicking a sample link.
