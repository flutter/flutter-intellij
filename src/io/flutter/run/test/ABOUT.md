<!--* freshness: { reviewed: '2026-08-31' } *-->
# Flutter Test Execution

## Overview
Configures, produces, and executes Flutter test run configurations within IntelliJ, handling test location resolution, test event conversion, and test debugging.

## Interface
- `DartTestLocationProviderZ`
- `FlutterTestConfigProducer`
- `FlutterTestConfigType`
- `FlutterTestConsoleProperties`
- `FlutterTestEventsConverter`
- `FlutterTestLineMarkerContributor`
- `FlutterTestLocationProvider`
- `FlutterTestRunner`
- `TestConfig`
- `TestConfigUtils`
- `TestDebugProcess`
- `TestFields`
- `TestForm`
- `TestLaunchState`

## Invariants
- `FlutterTestConfigType` specifies the run configuration type for Flutter tests.
- `TestConfig` encapsulates project-specific test configuration parameters and relies on `TestFields`.
- `FlutterTestConfigProducer` identifies whether a given PSI context is a valid Flutter test.

## Side Effects
- Spawns background Flutter test execution and debugging processes.
- Intercepts test runner output and converts JSON test events into IntelliJ test framework service messages.
- Modifies UI elements to render test execution results, status trees, and gutter line markers.

## Verification
- Build / Analysis: `./gradlew compileJava`
- Test: `./gradlew test`
