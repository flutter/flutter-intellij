<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter Refactoring

## Overview
Integrates with the Dart Analysis Server to provide Flutter-specific refactorings. Currently the package holds a single refactoring, Extract Widget.

[ExtractWidgetRefactoring](ExtractWidgetRefactoring.java) extends the Dart plugin's `com.jetbrains.lang.dart.ide.refactoring.ServerRefactoring`. Its consumer is [ExtractWidgetAction](../actions/ExtractWidgetAction.java), registered as `Flutter.ExtractWidget` in [plugin.xml](../../../../resources/META-INF/plugin.xml).

## Interface
- `ExtractWidgetRefactoring`
- `ExtractWidgetRefactoring.ExtractWidgetRefactoring(Project, VirtualFile, int, int)`
- `ExtractWidgetRefactoring.setName(String)`
- `ExtractWidgetRefactoring.sendOptions()`

## Invariants
- Always uses `RefactoringKind.EXTRACT_WIDGET`.
- Initializes `ExtractWidgetOptions` with a default name of 'NewWidget'.
- `setFeedback` is a no-op, so any feedback the server sends is discarded.

## Side Effects
- `setName(String)` mutates the internal `ExtractWidgetOptions` object state.
- `sendOptions()` triggers `setOptions(true, null)`, which communicates with the analysis server to apply the refactoring options.
