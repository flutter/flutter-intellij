<!--* freshness: { reviewed: '2026-10-25' } *-->
# Dart Analysis Server Protocol

## Overview
Data Transfer Objects (DTOs) representing requests, responses, and models for the Dart Analysis Server protocol.

## Interface
- [`ExtractWidgetOptions`](ExtractWidgetOptions.java) - Mutable options for the Extract Widget refactoring. Extends `RefactoringOptions`, which is not in this repo; it comes from the Dart plugin.
  - `ExtractWidgetOptions(String name)`, `getName()`, `setName(String)`
  - `static fromJson(JsonObject)`, `static fromJsonArray(JsonArray)`, `toJson()`
  - `EMPTY_ARRAY`, `EMPTY_LIST`
- [`RefactoringKind`](RefactoringKind.java) - Holds only `public static final String` constants: `CONVERT_GETTER_TO_METHOD`, `CONVERT_METHOD_TO_GETTER`, `EXTRACT_LOCAL_VARIABLE`, `EXTRACT_METHOD`, `EXTRACT_WIDGET`, `INLINE_LOCAL_VARIABLE`, `INLINE_METHOD`, `MOVE_FILE`, `RENAME`.

## Invariants
- Files in this directory are automatically generated (by `pkg/analysis_server/tool/spec/generate_files` in the Dart SDK) and must not be edited manually.
- Classes act as Data Transfer Objects (DTOs) for the Dart Analysis Server protocol.
- Only `ExtractWidgetOptions` provides JSON serialization (`fromJson`/`fromJsonArray`/`toJson`) and value-based `equals`/`hashCode`. `RefactoringKind` is a plain constants holder with neither.
- `ExtractWidgetOptions` depends on classes outside this repo: `RefactoringOptions` and `com.google.dart.server.utilities.general.ObjectUtilities` (from the Dart plugin), plus Guava and commons-lang3.

## Side Effects
- Memory allocation during object instantiation.
- No external I/O or observable side effects other than parsing and serializing JSON.
