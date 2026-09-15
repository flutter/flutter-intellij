<!--* freshness: { reviewed: '2026-08-17' } *-->
# Flutter Templates

## Overview
Provides Live Templates and file templates for Flutter and Dart code snippets to accelerate development.

## Interface
- `io.flutter.template.DartToplevelTemplateContextType`
- `io.flutter.template.DartToplevelTemplateContextType.DartToplevelTemplateContextType()`
- `io.flutter.template.FlutterLiveTemplatesProvider`
- `io.flutter.template.FlutterLiveTemplatesProvider.getDefaultLiveTemplateFiles()`
- `io.flutter.template.FlutterLiveTemplatesProvider.getHiddenLiveTemplateFiles()`

## Invariants
- `DartToplevelTemplateContextType` considers an element to be in context if and only if it is not inside a `DartClassDefinition` or a `PsiComment`.
- `FlutterLiveTemplatesProvider` always provides `/liveTemplates/flutter_miscellaneous` as default templates.
- `FlutterLiveTemplatesProvider` always returns `null` for hidden templates.

## Side Effects
- None.
