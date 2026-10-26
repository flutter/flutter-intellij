<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter Templates

## Overview
Provides Live Templates for Flutter and Dart code snippets to accelerate development. (There are no file templates in this package.)

## Interface
- [`io.flutter.template.DartToplevelTemplateContextType`](DartToplevelTemplateContextType.java) - Extends the Dart plugin's `DartTemplateContextType`; context id `DART_TOPLEVEL`, presentable name `Top-level`.
- `io.flutter.template.DartToplevelTemplateContextType.DartToplevelTemplateContextType()`
- [`io.flutter.template.FlutterLiveTemplatesProvider`](FlutterLiveTemplatesProvider.java)
- `io.flutter.template.FlutterLiveTemplatesProvider.getDefaultLiveTemplateFiles()`
- `io.flutter.template.FlutterLiveTemplatesProvider.getHiddenLiveTemplateFiles()`

## Resources & Registration
- Both classes are registered in [`plugin.xml`](../../../../resources/META-INF/plugin.xml) as `defaultLiveTemplatesProvider` and `liveTemplateContext contextId="DART_TOPLEVEL"`.
- Templates live in [`flutter_miscellaneous.xml`](../../../../resources/liveTemplates/flutter_miscellaneous.xml). Each template body is authored in a sibling `.txt` file (`stful.txt`, `stless.txt`, `stanim.txt`, `inh.txt`, `ihof.txt`, `mdof.txt`, `thof.txt`) and inlined into the XML by a regeneration step; see the [liveTemplates readme](../../../../resources/liveTemplates/readme.md).

## Invariants
- `DartToplevelTemplateContextType` considers an element to be in context if and only if it is not inside a `DartClassDefinition` or a `PsiComment`.
- `FlutterLiveTemplatesProvider` always provides `/liveTemplates/flutter_miscellaneous` as default templates.
- `FlutterLiveTemplatesProvider` always returns `null` for hidden templates.

## Side Effects
- None.
