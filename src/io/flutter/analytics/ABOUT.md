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
- Embedded browser event (`type=embedded_browser`) constants: `EMBEDDED_BROWSER_TYPE`, ids `EMBEDDED_BROWSER_OPENED` (`opened`) and `EMBEDDED_BROWSER_UNAVAILABLE` (`unavailable`), fields `ENGINE` (`ENGINE_JCEF` / `ENGINE_JXBROWSER`), `FEATURE` (the tool window id), `NEW_TAB`, and `REASON` with `REASON_TAB_CREATE_FAILED`, `REASON_ENGINE_NOT_READY`, and `REASON_LOAD_FAILED`.
- `object Analytics`
- `fun Analytics.report(data: AnalyticsData)`
- `fun Analytics.reportReloadOnSave(project: Project?, trigger: String, outcome: String, durationMs: Int?, errorCode: Int?)`: called from [`FlutterReloadManager`](../run/FlutterReloadManager.java).
- `fun Analytics.reportWidgetPreview(project: Project?, id: String, reason: String?)`: called from [`WidgetPreviewPanel`](../widgetpreview/WidgetPreviewPanel.java). Pass `reason` only with `failed`.
- `fun Analytics.reportEmbeddedBrowser(project: Project?, id: String, engine: String, feature: String, newTab: Boolean, reason: String?)`: called from [`EmbeddedBrowser.openPanel`](../view/EmbeddedBrowser.java). Pass `reason` only with `unavailable`.
- `fun Analytics.recordRunOrDebugSession(mechanism: String, executor: Executor, project: Project?)`: reports a private `SessionData` (an `AnalyticsData` subclass). Called from [`SdkRunConfig`](../run/SdkRunConfig.java), [`SdkAttachConfig`](../run/SdkAttachConfig.java), and [`TestConfig`](../run/test/TestConfig.java).

## Invariants
- `Analytics.report` fails silently by catching all Throwables to avoid crashing the IDE or debug/run sessions. This only protects callers of this wrapper; most plugin code (e.g., [`actions`](../actions/ABOUT.md), `run/daemon`, `sdk/FlutterCommand`) calls the Dart plugin's `Analytics.report` directly without this guard.
- The reload-on-save event carries the daemon's numeric error code, never its message, which can contain user code.
- The Widget Preview `failed` event carries a `REASON_*` value, never an exception message, which can contain VM service tokens or local paths. The same goes for the embedded browser `unavailable` event.
- Embedded browser events, one per `EmbeddedBrowser.openPanel` call:
  - `opened` means the URL was handed to the engine and the tab component was built, not that the page rendered.
  - Compute rates on `newTab=true`. `newTab=false` is a later `openPanel` for an existing tab: a Widget Preview theme reload, an Inspector re-open for the same device, or a tab that's stuck because it was created before the engine was ready. `refresh()`, `updateVmServiceUri()` and `updatePanelToWidget()` don't call `openPanel` and report nothing.
  - `tab_create_failed` doesn't cache a tab, so each retry reports it again with `newTab=true`.
  - `engine_not_ready` means there's no embedded tab: JxBrowser is installing, or its install failed or timed out.
  - Known gap: with JxBrowser, the Inspector checks the install status before calling `openPanel`, so its not-ready cases aren't reported here.
- At most one reload-on-save event per path per save: `skipped_syntax_error_any_editor` is reported at most once a minute. An explicit Save All runs both save paths, and the second one stays silent because the app is already reloading. When the Save All path reported an event, the document-save path it queued doesn't also report `skipped_syntax_error_any_editor` for the same save.
- Session type is set to `DEBUG_SESSION_TYPE` if `executor.id` matches `DefaultDebugExecutor.EXECUTOR_ID`, else `RUN_SESSION_TYPE`.

## Side Effects
- Sends analytics data to JetBrains Dart plugin analytics subsystem via `com.jetbrains.lang.dart.analytics.Analytics.report`.
