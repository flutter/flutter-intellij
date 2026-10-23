<!--* freshness: { reviewed: '2026-08-17' } *-->
# Dart Analysis Server Protocol

## Overview
Data Transfer Objects (DTOs) representing requests, responses, and models for the Dart Analysis Server protocol.

## Interface
- `class ExtractWidgetOptions`
- `class RefactoringKind`
- `ExtractWidgetOptions.fromJson(JsonObject)`

## Invariants
- Files in this directory are automatically generated and must not be edited manually.
- Classes act as Data Transfer Objects (DTOs) for the Dart Analysis Server protocol.
- Data classes provide bidirectional JSON serialization with static fromJson/fromJsonArray and instance toJson methods.
- Classes implement value-based equality via overridden equals and hashCode methods.
- Constants are provided via static final fields in specialized classes (e.g., RefactoringKind).

## Side Effects
- Memory allocation during object instantiation.
- No external I/O or observable side effects other than parsing and serializing JSON.
