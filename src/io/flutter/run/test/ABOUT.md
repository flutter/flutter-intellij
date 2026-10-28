<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter Test Execution

## Overview
Configures, produces, and executes Flutter test run configurations within IntelliJ, handling test location resolution, test event conversion, and test debugging.

## Interface
- [`DartTestLocationProviderZ`](DartTestLocationProviderZ.java)
- [`FlutterTestConfigProducer`](FlutterTestConfigProducer.java)
- [`FlutterTestConfigType`](FlutterTestConfigType.java)
- [`FlutterTestConsoleProperties`](FlutterTestConsoleProperties.java)
- [`FlutterTestEventsConverter`](FlutterTestEventsConverter.java): extends [`io.flutter.test.DartTestEventsConverterZ`](../../test/DartTestEventsConverterZ.java).
- [`FlutterTestLineMarkerContributor`](FlutterTestLineMarkerContributor.java)
- [`FlutterTestLocationProvider`](FlutterTestLocationProvider.java)
- [`FlutterTestRunner`](FlutterTestRunner.java)
- [`TestConfig`](TestConfig.java)
- [`TestConfigUtils`](TestConfigUtils.java)
- [`TestDebugProcess`](TestDebugProcess.java)
- [`TestFields`](TestFields.java)
- [`TestForm`](TestForm.java) (+ [`.form`](TestForm.form))
- [`TestLaunchState`](TestLaunchState.java)

## Invariants
- `FlutterTestConfigType` specifies the run configuration type for Flutter tests.
- `TestConfig` encapsulates project-specific test configuration parameters and relies on `TestFields`.
- `FlutterTestConfigProducer` creates directory-scoped (`TestFields.forDir`, only if the directory has tests), file-scoped, or name-scoped configs from the PSI context. File and name scopes require the file to be in a test directory; name-scoped `testWidgets` configs enable regexp matching. It replaces Dart plugin test configurations (`shouldReplace`).
- There are two console-properties classes: `TestConfig.createTestConsoleProperties` returns `FlutterTestConsoleProperties` (framework name `"FlutterWidgetTests"`, used by the VCS test-before-commit feature), while `TestLaunchState` builds its console with [`io.flutter.run.common.ConsoleProps`](../common/ConsoleProps.java).

## Side Effects
- Spawns background Flutter test execution and debugging processes.
- `TestConfig.getState` records analytics (`MECHANISM_FLUTTER_TESTS`).
- Intercepts test runner output and converts JSON test events into IntelliJ test framework service messages.
- Modifies UI elements to render test execution results, status trees, and gutter line markers.

## Verification
- Build / Analysis: `./gradlew compileJava`
- Test: `./gradlew test`
