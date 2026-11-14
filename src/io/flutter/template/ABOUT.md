<!--* freshness: { reviewed: '2026-11-03' } *-->
# Flutter Templates

## Overview
Provides Live Templates for Flutter and Dart code snippets to accelerate development. (There are no file templates in this package.)

## Interface
- [`io.flutter.template.DartToplevelTemplateContextType`](DartToplevelTemplateContextType.java) - Extends the Dart plugin's `DartTemplateContextType`; context id `DART_TOPLEVEL`, base context id `DART`, presentable name `Top-level`.
- `io.flutter.template.DartToplevelTemplateContextType.DartToplevelTemplateContextType()`

## Resources & Registration
- Registered declaratively in [`plugin.xml`](../../../../resources/META-INF/plugin.xml) via `<defaultLiveTemplates file="liveTemplates/flutter_miscellaneous.xml"/>` and `<liveTemplateContext contextId="DART_TOPLEVEL" baseContextId="DART" implementation="io.flutter.template.DartToplevelTemplateContextType"/>`.
- Templates live in [`flutter_miscellaneous.xml`](../../../../resources/liveTemplates/flutter_miscellaneous.xml). Each template body is authored in a sibling `.txt` file (`stful.txt`, `stless.txt`, `stanim.txt`, `inh.txt`, `ihof.txt`, `mdof.txt`, `thof.txt`) and inlined into the XML by a regeneration step; see the [liveTemplates readme](../../../../resources/liveTemplates/readme.md).

## Invariants
- `DartToplevelTemplateContextType` considers an element to be in context if and only if it is inside a Dart file (`baseContextId="DART"`) and not inside a `DartClassDefinition` or a `PsiComment`.

## Side Effects
- None.
