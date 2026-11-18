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
- Reload-on-save event (`type=hot_reload`, `id=reload_on_save`) constants: fields `TRIGGER`, `OUTCOME`, `ERROR_CODE` (the duration uses the Dart plugin's shared `duration_ms` key), and their values:
  - `TRIGGER_SAVE_ACTION` (`save_action`, an explicit Save All) and `TRIGGER_DOCUMENT_SAVE` (`document_save`, autosave and other IDE saves).
  - `OUTCOME_SUCCESS`, `OUTCOME_ERROR` (with the daemon's `errorCode`), `OUTCOME_EXCEPTION` (no result, e.g. a daemon error response), `OUTCOME_SKIPPED_NOT_CONNECTED`, `OUTCOME_SKIPPED_SYNTAX_ERROR_CURRENT_FILE`, and `OUTCOME_SKIPPED_SYNTAX_ERROR_ANY_EDITOR`.
- Widget Preview event (`type=widget_preview`) constants: `WIDGET_PREVIEW_TYPE`, ids `WIDGET_PREVIEW_STARTED` (`started`, the preview URL arrived) and `WIDGET_PREVIEW_FAILED` (`failed`), field `REASON`, and its values `REASON_NO_SDK`, `REASON_SDK_UNKNOWN_VERSION`, `REASON_SDK_TOO_OLD`, `REASON_NO_PUB_ROOT`, `REASON_PROCESS_ERROR`, `REASON_NO_URL`, and `REASON_EXEC_FAILED`.
- `object Analytics`
- `fun Analytics.report(data: AnalyticsData)`
- `fun Analytics.reportReloadOnSave(project: Project?, trigger: String, outcome: String, durationMs: Int?, errorCode: Int?)`: called from [`FlutterReloadManager`](../run/FlutterReloadManager.java).
- `fun Analytics.reportWidgetPreview(project: Project?, id: String, reason: String?)`: called from [`WidgetPreviewPanel`](../widgetpreview/WidgetPreviewPanel.java). Pass `reason` only with `failed`.
- `fun Analytics.recordRunOrDebugSession(mechanism: String, executor: Executor, project: Project?)`: reports a private `SessionData` (an `AnalyticsData` subclass). Called from [`SdkRunConfig`](../run/SdkRunConfig.java), [`SdkAttachConfig`](../run/SdkAttachConfig.java), and [`TestConfig`](../run/test/TestConfig.java).

## Invariants
- `Analytics.report` fails silently by catching all Throwables to avoid crashing the IDE or debug/run sessions. This only protects callers of this wrapper; most plugin code (e.g., [`actions`](../actions/ABOUT.md), `run/daemon`, `sdk/FlutterCommand`) calls the Dart plugin's `Analytics.report` directly without this guard.
- The reload-on-save event carries the daemon's numeric error code, never its message, which can contain user code.
- The Widget Preview `failed` event carries a `REASON_*` value, never an exception message, which can contain VM service tokens or local paths.
- At most one reload-on-save event per path per save: `skipped_syntax_error_any_editor` is reported at most once a minute. An explicit Save All runs both save paths, and the second one stays silent because the app is already reloading. When the Save All path reported an event, the document-save path it queued doesn't also report `skipped_syntax_error_any_editor` for the same save.
- Session type is set to `DEBUG_SESSION_TYPE` if `executor.id` matches `DefaultDebugExecutor.EXECUTOR_ID`, else `RUN_SESSION_TYPE`.

## Side Effects
- Sends analytics data to JetBrains Dart plugin analytics subsystem via `com.jetbrains.lang.dart.analytics.Analytics.report`.
