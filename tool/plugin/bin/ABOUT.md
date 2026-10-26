<!--* freshness: { reviewed: '2026-10-25' } *-->
# plugin/bin

## Overview
Entry point for the plugin tool: [`main.dart`](main.dart). The repo-root wrapper [`bin/plugin`](../../../bin/plugin) runs it as `dart tool/plugin/bin/main.dart "$@"`.

## Interface
- `main(List<String> arguments) async`

## Invariants
- Must be run with the working directory set to the project root directory

## Side Effects
- Executes `plugin.main(arguments)` from [`../lib/plugin.dart`](../lib/plugin.dart)
- Terminates the process with exit() using the returned code

## Verification
- Build / Analysis: `dart analyze`
- Test: `dart test`
