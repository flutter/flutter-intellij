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
- `const val AnalyticsConstants.HOT_RELOAD_TYPE`, `RELOAD_ON_SAVE_ID`: type and id of the reload-on-save event.
- `val AnalyticsConstants.TRIGGER`, `OUTCOME` (`StringValue`) and `ERROR_CODE` (`IntValue`): its fields. The duration uses the Dart plugin's shared `duration_ms` key (`com.jetbrains.lang.dart.analytics.AnalyticsConstants.DURATION_MS`).
- `const val AnalyticsConstants.TRIGGER_SAVE_ACTION`, `TRIGGER_DOCUMENT_SAVE`: trigger values.
  - `save_action`: an explicit Save All.
  - `document_save`: every other save-all, such as autosave and saves made by other IDE features.
- `const val AnalyticsConstants.OUTCOME_*`:
  - `success`: flutter_tools reported success.
  - `error`: flutter_tools returned a failed result; `errorCode` is its code, which is 1 for almost every failure. Use flutter_tools' `hotRunnerInfo` labels for failure reasons.
  - `exception`: no result arrived, because the daemon answered with an error response or the reload couldn't be sent.
  - `skipped_not_connected`: the app started shutting down before the reload was sent.
  - `skipped_syntax_error_current_file`: the saved file has a parse error (the user sees "Reload not performed").
  - `skipped_syntax_error_any_editor`: some open editor has a parse error; skipped silently, reported at most once a minute.
- `class ReloadOnSaveData`: the reload-on-save event. Clamps the duration to `Int`, because the Dart reporter drops `Long` values.
- `object Analytics`
- `fun Analytics.report(data: AnalyticsData)`
- `fun Analytics.reportReloadOnSave(project: Project?, trigger: String, outcome: String, durationMs: Long? = null, errorCode: Int? = null)`: called from [`FlutterReloadManager`](../run/FlutterReloadManager.java).
- `fun Analytics.recordRunOrDebugSession(mechanism: String, executor: Executor, project: Project?)`: reports a private `SessionData` (an `AnalyticsData` subclass). Called from [`SdkRunConfig`](../run/SdkRunConfig.java), [`SdkAttachConfig`](../run/SdkAttachConfig.java), and [`TestConfig`](../run/test/TestConfig.java).

## Invariants
- `Analytics.report` fails silently by catching all Throwables to avoid crashing the IDE or debug/run sessions. This only protects callers of this wrapper; most plugin code (e.g., [`actions`](../actions/ABOUT.md), `run/daemon`, `sdk/FlutterCommand`) calls the Dart plugin's `Analytics.report` directly without this guard.
- The reload-on-save event never includes the daemon's error message, only its numeric code, because the message can contain user code.
- There is at most one reload-on-save event per attempt that gets past the cheap guards (unsupported app, app not started, or a reload already in progress): exactly one, except that `skipped_syntax_error_any_editor` is rate-limited.
- Outcomes depend on the trigger: `skipped_syntax_error_any_editor` only occurs with `document_save`, and `skipped_syntax_error_current_file` almost only with `save_action`. Segment dashboards by `trigger`.
- Register `trigger`, `outcome`, and `errorCode` as GA4 custom dimensions (`errorCode` as a dimension, not a metric).
- `flutter_tools` reports its own `hotRunnerInfo` events (with `reason=save`) for the reloads that reach it. The reload-on-save event is the IDE's view of the attempt: don't sum the two.
- Session type is set to `DEBUG_SESSION_TYPE` if `executor.id` matches `DefaultDebugExecutor.EXECUTOR_ID`, else `RUN_SESSION_TYPE`.

## Side Effects
- Sends analytics data to JetBrains Dart plugin analytics subsystem via `com.jetbrains.lang.dart.analytics.Analytics.report`.
