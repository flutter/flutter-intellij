<!--* freshness: { reviewed: '2026-08-17' } *-->
# Flutter Refactoring

## Overview
Integrates with the Dart Analysis Server to provide Flutter-specific refactoring operations, such as extracting widgets.

## Interface
- `ExtractWidgetRefactoring`
- `ExtractWidgetRefactoring.ExtractWidgetRefactoring(Project, VirtualFile, int, int)`
- `ExtractWidgetRefactoring.setName(String)`
- `ExtractWidgetRefactoring.sendOptions()`

## Invariants
- Always uses `RefactoringKind.EXTRACT_WIDGET`.
- Initializes `ExtractWidgetOptions` with a default name of 'NewWidget'.

## Side Effects
- `setName(String)` mutates the internal `ExtractWidgetOptions` object state.
- `sendOptions()` triggers `setOptions(true, null)`, which communicates with the analysis server to apply the refactoring options.
