<!--* freshness: { reviewed: '2026-08-17' } *-->
# Flutter Run Common

## Overview
Shared utilities and configurations for Flutter test, coverage, and run execution modes.

## Interface
- `CommonTestConfigUtils`
- `ConsoleProps`
- `RunMode`
- `TestLineMarkerContributor`
- `TestType`

## Invariants
- A test call is identified as TestType.SINGLE if marked UNIT_TEST_TEST, and TestType.GROUP if marked UNIT_TEST_GROUP.
- RunMode values DEBUG and RUN support hot reload; COVERAGE and PROFILE do not.
- ConsoleProps configures test event parsing with idBasedTestTree=true and usePredefinedMessageFilter=false.
- Test line markers are attached to leaf nodes of the PSI tree matching a Dart unit test.
- Test line markers visually reflect the state magnitude (Passed, Failed, Ignored) by deriving test states from TestStateStorage.
- TestType.MAIN does not detect main methods on its own, it defers to CommonTestConfigUtils.

## Side Effects
- CommonTestConfigUtils mutates internal caching state to store outline test types and listeners for files.
- CommonTestConfigUtils registers LineMarkerUpdatingListener with the ActiveEditorsOutlineService.
- CommonTestConfigUtils asynchronously restarts DaemonCodeAnalyzer on the default ModalityState when outline changes.
- RunMode.fromEnv throws an ExecutionException if the execution environment run mode string is unsupported.
- TestLineMarkerContributor dynamically queries TestStateStorage for recent test states.
