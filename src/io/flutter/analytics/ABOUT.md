<!--* freshness: { reviewed: '2026-10-25' } *-->
# Flutter Analytics

## Overview
Handles reporting usage metrics and analytics events for the Flutter plugin via the Dart plugin analytics subsystem. Everything lives in [`Analytics.kt`](Analytics.kt).

## Interface
- `object AnalyticsConstants`
- `val AnalyticsConstants.GOOGLE3` (currently unreferenced in `src/`)
- `val AnalyticsConstants.MISSING_SDK`
- `val AnalyticsConstants.REQUIRES_RESTART`
- `const val AnalyticsConstants.MECHANISM_FLUTTER_ATTACH`
- `const val AnalyticsConstants.MECHANISM_FLUTTER_APP`
- `const val AnalyticsConstants.MECHANISM_FLUTTER_TESTS`
- `const val AnalyticsConstants.DEBUG_SESSION_TYPE`
- `const val AnalyticsConstants.RUN_SESSION_TYPE`
- `object Analytics`
- `fun Analytics.report(data: AnalyticsData)`
- `fun Analytics.recordRunOrDebugSession(mechanism: String, executor: Executor, project: Project?)`: reports a private `SessionData` (an `AnalyticsData` subclass). Called from [`SdkRunConfig`](../run/SdkRunConfig.java), [`SdkAttachConfig`](../run/SdkAttachConfig.java), and [`TestConfig`](../run/test/TestConfig.java).

## Invariants
- `Analytics.report` fails silently by catching all Throwables to avoid crashing the IDE or debug/run sessions. This only protects callers of this wrapper; most plugin code (e.g., [`actions`](../actions/ABOUT.md), `run/daemon`, `sdk/FlutterCommand`) calls the Dart plugin's `Analytics.report` directly without this guard.
- Session type is set to `DEBUG_SESSION_TYPE` if `executor.id` matches `DefaultDebugExecutor.EXECUTOR_ID`, else `RUN_SESSION_TYPE`.

## Side Effects
- Sends analytics data to JetBrains Dart plugin analytics subsystem via `com.jetbrains.lang.dart.analytics.Analytics.report`.
