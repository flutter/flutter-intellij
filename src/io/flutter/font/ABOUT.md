<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter Font Preview

## Overview
Discovers, analyzes, and provides IDE previews for custom fonts and icon packs in Flutter projects.

Key files:
- [FontPreviewProcessor.java](FontPreviewProcessor.java): an application service (registered as `applicationService` in [plugin.xml](../../../../resources/META-INF/plugin.xml)) that discovers icon classes in the font packages configured in `FlutterSettings`. It feeds the results to [FlutterIconLineMarkerProvider](../editor/FlutterIconLineMarkerProvider.java).
- [FontPreviewStartupActivity.kt](FontPreviewStartupActivity.kt): a Kotlin `ProjectActivity`, registered as a `backgroundPostStartupActivity`, that starts analysis when a project opens.

## Interface
- `FontPreviewProcessor.PACKAGE_SEPARATORS`
- `FontPreviewProcessor.UNSUPPORTED_PACKAGES`
- `FontPreviewProcessor.analyze(Project project)`: gets the service through `Application.getService` and calls `generate`.
- `FontPreviewProcessor.reanalyze(Project project)`: clears the project's caches, then calls `analyze`.
- `FontPreviewProcessor.generate(Project project)`
- `FontPreviewStartupActivity`
- `FontPreviewStartupActivity.execute(Project project)`

`processItems` and `processNextItem` are package-private helpers that drive the background queue. They are not public API.

## Internals
- `WorkItem` holds the per-project queues: packages, files to rewrite, files to analyze, files to check, files to delete, and classes to analyze. `ClassInfo`, `FileInfo` and `PathInfo` are small value holders for these queues.
- `checkNextFile` follows `export` statements (matched by `EXPORT_STATEMENT_PATTERN`) so that icon aggregator packages that re-export other libraries can be analyzed.
- `isInSdk` excludes files inside the Flutter SDK from analysis.

## Invariants
- `FontPreviewStartupActivity` skips analysis if the application is in unit test mode.
- `FontPreviewProcessor` maintains a set of `ANALYZED_PROJECT_FILES` (keyed by project base path) to prevent redundant analysis unless caches are cleared.
- Certain packages like `flutter_icons`, `flutter_vector_icons`, and `material_design_icons_flutter` are marked as unsupported. They are still enqueued by `generate`, but `findFontFiles` skips them.
- `WORK_ITEMS` queues background operations per project to discover, analyze, rewrite, check, and delete font files.
- `ANALYZED_PROJECT_FILES` and `WORK_ITEMS` are plain static `HashMap`s shared by all projects and touched from background tasks. They are not synchronized.

## Side Effects
- Registers a `ProjectManagerListener` that clears caches when a project is closed. Clearing removes the project's `ANALYZED_PROJECT_FILES` and `WORK_ITEMS` entries and calls `FlutterIconLineMarkerProvider.initialize()`, which resets `KnownPaths` to the built-in defaults (`Icons`, `IconData`, `CupertinoIcons`) rather than emptying it.
- Executes file I/O to create temporary files (by stripping imports) and deletes them after analysis.
- Restarts the `DaemonCodeAnalyzer` when background analysis finishes after more than 1 second, so highlighting picks up icon classes found late. A cancelled task always clears the project caches and restarts the analyzer. A finish in under 1 second does not restart it.
- Populates `FlutterIconLineMarkerProvider.KnownPaths` with discovered icon classes and their source file paths.
- Reads font packages from `FlutterSettings.getInstance().getFontPackages()`.
