<!--* freshness: { reviewed: '2026-08-17' } *-->
# Dart Analysis Server Protocol

## Overview
Data Transfer Objects (DTOs) representing requests, responses, and models for the Dart Analysis Server protocol.

## Interface
- `class ExtractWidgetFeedback`
- `class ExtractWidgetOptions`
- `class FlutterOutline`
- `class FlutterOutlineAttribute`
- `class FlutterOutlineKind`
- `class FlutterService`
- `class FlutterWidgetProperty`
- `class FlutterWidgetPropertyEditor`
- `class FlutterWidgetPropertyEditorKind`
- `class FlutterWidgetPropertyValue`
- `class FlutterWidgetPropertyValueEnumItem`
- `class RefactoringKind`
- `class RequestErrorCode`
- `ExtractWidgetFeedback.fromJson(JsonObject)`
- `ExtractWidgetOptions.fromJson(JsonObject)`
- `FlutterOutline.fromJson(JsonObject)`
- `FlutterOutlineAttribute.fromJson(JsonObject)`
- `FlutterWidgetProperty.fromJson(JsonObject)`
- `FlutterWidgetPropertyEditor.fromJson(JsonObject)`
- `FlutterWidgetPropertyValue.fromJson(JsonObject)`
- `FlutterWidgetPropertyValueEnumItem.fromJson(JsonObject)`

## Invariants
- Files in this directory are automatically generated and must not be edited manually.
- Classes act as Data Transfer Objects (DTOs) for the Dart Analysis Server protocol.
- Data classes provide bidirectional JSON serialization with static fromJson/fromJsonArray and instance toJson methods.
- Classes implement value-based equality via overridden equals and hashCode methods.
- Constants are provided via static final fields in specialized classes (e.g., FlutterOutlineKind, RefactoringKind, RequestErrorCode).

## Side Effects
- Memory allocation during object instantiation.
- No external I/O or observable side effects other than parsing and serializing JSON.
