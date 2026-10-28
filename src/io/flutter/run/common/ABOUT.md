<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter Run Common

## Overview
Shared utilities and configurations for Flutter test, coverage, and run execution modes.

## Interface
- [`CommonTestConfigUtils`](CommonTestConfigUtils.java)
- [`ConsoleProps`](ConsoleProps.java)
- [`RunMode`](RunMode.java)
- [`TestLineMarkerContributor`](TestLineMarkerContributor.java)
- [`TestType`](TestType.java)

## Invariants
- A test call is identified as TestType.SINGLE for single test calls (`test`, `testWidgets`, or `@isTest`-annotated functions), and TestType.GROUP for test group calls (`group` or `@isTestGroup`-annotated functions).
- RunMode values DEBUG and RUN support hot reload; COVERAGE and PROFILE do not.
- RunMode.fromEnv maps the executor ID to a mode; PROFILE corresponds to `LaunchState.ANDROID_PROFILER_EXECUTOR_ID`. `RunMode.isProfiling()` is true only for PROFILE.
- ConsoleProps configures test event parsing with idBasedTestTree=true and usePredefinedMessageFilter=false.
- ConsoleProps always registers the framework name `"FlutterTestRunner"`; the `testFrameworkName` constructor argument (passed by `forPub` from `pubFrameworkName`) is currently ignored.
- ConsoleProps uses `FlutterTestLocationProvider.INSTANCE` as the test locator and [`FlutterTestEventsConverter`](../test/FlutterTestEventsConverter.java) to parse events. "Rerun failed tests" is not implemented (`createRerunFailedTestsAction` returns null).
- Test line markers are attached to leaf nodes of the PSI tree matching a Dart unit test.
- Test line markers visually reflect the state magnitude (Passed, Failed, Ignored) by deriving test states from TestStateStorage.
- TestType.MAIN does not detect main methods on its own, it defers to CommonTestConfigUtils.

## Side Effects
- CommonTestConfigUtils caches resolved custom test annotation types per file using CachedValuesManager.
- RunMode.fromEnv throws an ExecutionException if the execution environment run mode string is unsupported.
- TestLineMarkerContributor dynamically queries TestStateStorage for recent test states.
