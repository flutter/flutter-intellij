<!--* freshness: { reviewed: '2026-11-10' } *-->
# Flutter Refactoring

## Overview
Integrates with the Dart Analysis Server to provide Flutter-specific refactorings. Currently the package supports the Extract Widget refactoring via two mechanisms:
1. Modern LSP code actions routed via [FlutterLspRefactoringHelper](FlutterLspRefactoringHelper.kt) when `FlutterLspRefactoringHelper.isLspCodeActionsEnabled(project)` is true.
2. Legacy Analysis Server LTK protocol routed via [ExtractWidgetRefactoring](ExtractWidgetRefactoring.java) (deprecated) when LSP code actions are disabled.

Both mechanisms are invoked by [ExtractWidgetAction](../actions/ExtractWidgetAction.java), registered as `Flutter.ExtractWidget` in [plugin.xml](../../../../resources/META-INF/plugin.xml).

## Interface
### LSP Refactoring (Preferred)
- `FlutterLspRefactoringHelper`
- `FlutterLspRefactoringHelper.isLspCodeActionsEnabled(Project)`
- `FlutterLspRefactoringHelper.performExtractWidget(Project, Editor, PsiFile, int, int, LspServer)`
- `FlutterLspRefactoringHelper.performRefactoring(...)`

### Legacy LTK Refactoring (Deprecated)
- `ExtractWidgetRefactoring`
- `ExtractWidgetRefactoring.ExtractWidgetRefactoring(Project, VirtualFile, int, int)`
- `ExtractWidgetRefactoring.setName(String)`
- `ExtractWidgetRefactoring.sendOptions()`

## Invariants
- When LSP code actions are enabled, `textDocument/codeAction` requests are sent with kind `refactor.extract` (`CodeActionKind.RefactorExtract`), and matching actions titled `"Extract Widget"` are executed as `LspIntentionAction`.
- In legacy mode, always uses `RefactoringKind.EXTRACT_WIDGET` with default name `'NewWidget'`.

## Side Effects
- LSP refactorings apply workspace edits and launch linked editing for inline renaming.
- Legacy `sendOptions()` triggers `setOptions(true, null)` via the legacy analysis server protocol.
