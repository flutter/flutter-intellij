<!--* freshness: { reviewed: '2026-08-17' } *-->
# plugin/bin

## Overview
Entry point for the plugin tool.

## Interface
- `main(List<String> arguments) async`

## Invariants
- Must be run with the working directory set to the project root directory

## Side Effects
- Executes plugin.main(arguments)
- Terminates the process with exit() using the returned code

## Verification
- Build / Analysis: `dart analyze`
- Test: `dart test`
