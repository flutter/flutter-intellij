<!--* freshness: { reviewed: '2026-09-07' } *-->
# Flutter Project Structure

## Overview
Manages the IDE's recognition of Flutter projects, organizing modules and detecting project structure changes.

## Interface
- `FlutterIconProvider`
- `FlutterModuleGroup`
- `FlutterProjectStructureDetector`

## Invariants
- FlutterIconProvider only provides icons if the project is recognized as a Flutter module.
- FlutterModuleGroup has a static set of inner classes representing different project types (App, Mod, Plugin, Package) with fixed weights for sorting.
- FlutterProjectStructureDetector differentiates root directories based on whether they contain a PubRoot that declares Flutter, separating them into FlutterProjectRoot or DartProjectRoot.

## Side Effects
- FlutterProjectStructureDetector.setupProjectStructure subscribes to the application message bus and schedules an asynchronous task on a pooled thread (with a 5-minute timeout) to add Android submodules when the project opens.
