<!--* freshness: { reviewed: '2026-08-17' } *-->
# Flutter Analytics

## Overview
Handles reporting usage metrics and analytics events for the Flutter plugin via the Dart plugin analytics subsystem.

## Interface
- `object AnalyticsConstants`
- `val AnalyticsConstants.GOOGLE3`
- `val AnalyticsConstants.MISSING_SDK`
- `val AnalyticsConstants.REQUIRES_RESTART`
- `const val AnalyticsConstants.MECHANISM_FLUTTER_ATTACH`
- `const val AnalyticsConstants.MECHANISM_FLUTTER_APP`
- `const val AnalyticsConstants.MECHANISM_FLUTTER_TESTS`
- `const val AnalyticsConstants.DEBUG_SESSION_TYPE`
- `const val AnalyticsConstants.RUN_SESSION_TYPE`
- `object Analytics`
- `fun Analytics.report(data: AnalyticsData)`
- `fun Analytics.recordRunOrDebugSession(mechanism: String, executor: Executor, project: Project?)`

## Invariants
- Reporting analytics fails silently by catching all Throwables to avoid crashing the IDE or debug/run sessions.
- Session type is set to `DEBUG_SESSION_TYPE` if `executor.id` matches `DefaultDebugExecutor.EXECUTOR_ID`, else `RUN_SESSION_TYPE`.

## Side Effects
- Sends analytics data to JetBrains Dart plugin analytics subsystem via `com.jetbrains.lang.dart.analytics.Analytics.report`.
