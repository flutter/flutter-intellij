<!--* freshness: { reviewed: '2026-08-17' } *-->
# Flutter Font Preview

## Overview
Discovers, analyzes, and provides IDE previews for custom fonts and icon packs in Flutter projects.

## Interface
- `FontPreviewProcessor.PACKAGE_SEPARATORS`
- `FontPreviewProcessor.UNSUPPORTED_PACKAGES`
- `FontPreviewProcessor.analyze(Project project)`
- `FontPreviewProcessor.reanalyze(Project project)`
- `FontPreviewProcessor.generate(Project project)`
- `FontPreviewProcessor.processItems(Project project)`
- `FontPreviewProcessor.processNextItem(Project project, WorkItem item)`
- `FontPreviewStartupActivity`
- `FontPreviewStartupActivity.execute(Project project)`

## Invariants
- `FontPreviewStartupActivity` skips analysis if the application is in unit test mode.
- `FontPreviewProcessor` maintains a set of `ANALYZED_PROJECT_FILES` (keyed by project base path) to prevent redundant analysis unless caches are cleared.
- Certain packages like `flutter_icons`, `flutter_vector_icons`, and `material_design_icons_flutter` are marked as unsupported and skipped during analysis.
- `WORK_ITEMS` queues background operations per project to discover, analyze, rewrite, check, and delete font files.

## Side Effects
- Registers a `ProjectManagerListener` that clears caches (`ANALYZED_PROJECT_FILES`, `WORK_ITEMS`, and `FlutterIconLineMarkerProvider`) when a project is closed.
- Executes file I/O to create temporary files (by stripping imports) and deletes them after analysis.
- Restarts the `DaemonCodeAnalyzer` when background analysis takes longer than 1 second or finishes/cancels.
- Populates `FlutterIconLineMarkerProvider.KnownPaths` with discovered icon classes and their source file paths.
- Reads font packages from `FlutterSettings.getInstance().getFontPackages()`.
