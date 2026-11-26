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
- Run/debug session fields `EXECUTOR` (`EXECUTOR_RUN`, `EXECUTOR_DEBUG`, `EXECUTOR_COVERAGE`, `EXECUTOR_PROFILER`, `EXECUTOR_OTHER`) and `BUILD_MODE` (`BUILD_MODE_DEBUG`, `BUILD_MODE_PROFILE`, `BUILD_MODE_RELEASE`).
- App and attach session device fields `PLATFORM_TYPE` (`PLATFORM_TYPE_*`), `DEVICE_CATEGORY` (`DEVICE_CATEGORY_*`), `IS_EMULATOR` and `HAS_FLAVOR`.
- `OUTCOME` (`outcome`), shared by the reload-on-save and build events, with the shared values `OUTCOME_SUCCESS` and `OUTCOME_ERROR`.
- Reload-on-save event (`type=hot_reload`, `id=reload_on_save`) constants: fields `TRIGGER`, `OUTCOME`, `ERROR_CODE` (the duration uses the Dart plugin's shared `duration_ms` key), and their values:
  - `TRIGGER_SAVE_ACTION` (`save_action`, an explicit Save All) and `TRIGGER_DOCUMENT_SAVE` (`document_save`, autosave and other IDE saves).
  - `OUTCOME_SUCCESS`, `OUTCOME_ERROR` (with the daemon's `errorCode`), `OUTCOME_EXCEPTION` (no result, e.g. a daemon error response), `OUTCOME_SKIPPED_NOT_CONNECTED`, `OUTCOME_SKIPPED_SYNTAX_ERROR_CURRENT_FILE`, and `OUTCOME_SKIPPED_SYNTAX_ERROR_ANY_EDITOR`.
- Widget Preview event (`type=widget_preview`) constants: `WIDGET_PREVIEW_TYPE`, ids `WIDGET_PREVIEW_STARTED` (`started`, the preview URL arrived) and `WIDGET_PREVIEW_FAILED` (`failed`), field `REASON`, and its values `REASON_NO_SDK`, `REASON_SDK_UNKNOWN_VERSION`, `REASON_SDK_TOO_OLD`, `REASON_NO_PUB_ROOT`, `REASON_PROCESS_ERROR`, `REASON_NO_URL`, and `REASON_EXEC_FAILED`.
- Embedded browser event (`type=embedded_browser`) constants: `EMBEDDED_BROWSER_TYPE`, ids `EMBEDDED_BROWSER_OPENED` (`opened`) and `EMBEDDED_BROWSER_FAILED` (`failed`), fields `ENGINE` (`ENGINE_JCEF` / `ENGINE_JXBROWSER`), `FEATURE` (the tool window id), `NEW_TAB`, and `REASON` with `REASON_TAB_CREATE_FAILED`, `REASON_ENGINE_NOT_READY`, and `REASON_LOAD_FAILED`.
- Build event (`type=flutter_build`, `id=build_menu`) constants: `BUILD_TYPE`, `BUILD_MENU`, fields `BUILD_TARGET` and `EXIT_CODE` (plus `OUTCOME` and the Dart plugin's shared `duration_ms` key), and `OUTCOME_NOT_STARTED`.
- `object Analytics`
- `fun Analytics.report(data: AnalyticsData)`
- `fun Analytics.reportReloadOnSave(project: Project?, trigger: String, outcome: String, durationMs: Int?, errorCode: Int?)`: called from [`FlutterReloadManager`](../run/FlutterReloadManager.java).
- `fun Analytics.reportWidgetPreview(project: Project?, id: String, reason: String?)`: called from [`WidgetPreviewPanel`](../widgetpreview/WidgetPreviewPanel.java). Pass `reason` only with `failed`.
- `fun Analytics.reportEmbeddedBrowser(project: Project?, id: String, engine: String, feature: String, newTab: Boolean, reason: String?)`: called from [`EmbeddedBrowser.openPanel`](../view/EmbeddedBrowser.java). Pass `reason` only with `failed`.
- `fun Analytics.reportBuild(project: Project?, buildTarget: String, exitCode: Int, durationMs: Int)` and `fun Analytics.reportBuildNotStarted(project: Project?, buildTarget: String)`: called from [`FlutterBuildActionGroup.build`](../actions/FlutterBuildActionGroup.java) when a `flutter build` process ends, or when it couldn't be launched.
- `fun Analytics.recordRunOrDebugSession(mechanism: String, executor: Executor, project: Project?, buildMode: String? = null, device: FlutterDevice? = null, hasFlavor: Boolean? = null)`: reports a private `SessionData` (an `AnalyticsData` subclass). [`SdkRunConfig`](../run/SdkRunConfig.java) and [`SdkAttachConfig`](../run/SdkAttachConfig.java) set it as their `LaunchState`'s session reporter, which runs once the launch has a device and an app; [`TestConfig`](../run/test/TestConfig.java) calls it from `getState`, without a build mode or device.
- `fun Analytics.buildModeFor(envMode: FlutterLaunchMode, executorId: String?, extraArgs: Array<String>): String`: a session's `BUILD_MODE_*` value. (`executorFor`, which maps an executor id to an `EXECUTOR_*` value, is `@VisibleForTesting`.)
- `fun Analytics.hasFlavor(buildFlavor: String?, extraArgs: Array<String>): Boolean`: whether an app session builds a flavor. (`platformTypeFor` and `deviceCategoryFor` are `@VisibleForTesting`.)

## Invariants
- `Analytics.report` fails silently by catching all Throwables to avoid crashing the IDE or debug/run sessions. This only protects callers of this wrapper; most plugin code (e.g., [`actions`](../actions/ABOUT.md), `run/daemon`, `sdk/FlutterCommand`) calls the Dart plugin's `Analytics.report` directly without this guard.
- The reload-on-save event carries the daemon's numeric error code, never its message, which can contain user code.
- The Widget Preview `failed` event carries a `REASON_*` value, never an exception message, which can contain VM service tokens or local paths. The same goes for the embedded browser `failed` event.
- Build events, one per `flutter build` started from the Build menu (one per pub root when the action has no pub root): `buildTarget` is the build type (`aar`, `apk`, `appbundle`, `bundle`, `ios`, `linux`, `macos`, `web`, `windows`); `outcome` is `success` (exit code 0), `error` (any other exit code) or `not_started` (the flutter process couldn't be launched, e.g. a missing or non-executable `flutter` script; no exit code or duration); `exitCode` and `duration_ms` (from launch to process exit) are set when it ran. There's no in-IDE stop for a build, and flutter_tools exits 0 on SIGINT/SIGTERM, so an interrupted build may report `success`. The Build menu click is still reported as `flutter.build.<type>`. The plugin passes no mode flag, so the target determines the mode: `bundle` builds debug, `aar` builds debug, profile and release, and the other targets build release.
- Embedded browser events, one per `EmbeddedBrowser.openPanel` call:
  - `opened` means the URL was handed to the engine and the tab component was built, not that the page rendered.
  - Compute rates on `newTab=true`. `newTab=false` is a later `openPanel` for an existing tab: a Widget Preview theme reload, an Inspector re-open for the same device, or a tab that's stuck because it was created before the engine was ready. `refresh()`, `updateVmServiceUri()` and `updatePanelToWidget()` don't call `openPanel` and report nothing.
  - `tab_create_failed` doesn't cache a tab, so each retry reports it again with `newTab=true`.
  - `engine_not_ready` means there's no embedded tab: JxBrowser is installing, or its install failed or timed out.
  - Known gap: with JxBrowser, the Inspector checks the install status before calling `openPanel`, so its not-ready cases aren't reported here.
- At most one reload-on-save event per path per save: `skipped_syntax_error_any_editor` is reported at most once a minute. An explicit Save All runs both save paths, and the second one stays silent because the app is already reloading. When the Save All path reported an event, the document-save path it queued doesn't also report `skipped_syntax_error_any_editor` for the same save.
- Session type is set to `DEBUG_SESSION_TYPE` if `executor.id` matches `DefaultDebugExecutor.EXECUTOR_ID`, else `RUN_SESSION_TYPE`.
- Session events carry `executor`, mapped to a fixed value, never the raw executor id. `executor=debug` (like `type=debug_session`) means a debugger is attached; `buildMode=debug` means a debug (JIT) build.
- App and attach sessions also carry `buildMode`: `release`/`profile` for exactly `--release`/`--profile` in the configuration's extra args (additional args for run, attach args for attach), `profile` for the Android Profiler executor, and otherwise the environment's `FlutterLaunchMode`. The args are never sent. Test sessions have no build mode.
- App and attach sessions are reported from `LaunchState.launch`/`AttachState.launch` once the device is selected and an app was created, so launches aborted for lack of a device, and re-runs that `LaunchState.Runner.doExecute` turns into a hot restart, aren't counted (a re-run with changed arguments relaunches, and is). Test sessions are still reported from `getState`. `recordRunOrDebugSession` catches all Throwables, so it can't fail a launch.
- App and attach sessions carry `platformType` (the daemon's device `platformType`: android/ios/web/macos/linux/windows/fuchsia/custom, else `other`; not `targetPlatform`, which flutter_tools' own analytics use for values like `android-arm64`) and `deviceCategory` (`mobile`/`web`/`desktop`, else `other`). `isEmulator` is sent only for Android and iOS (flutter_tools reports false for every other device). App sessions also carry `hasFlavor` (a flavor in the run configuration, or `--flavor` in its additional args; a pubspec `default-flavor` isn't detected); attach sessions don't, as `flutter attach` doesn't build. Never the device id or name.

## Side Effects
- Sends analytics data to JetBrains Dart plugin analytics subsystem via `com.jetbrains.lang.dart.analytics.Analytics.report`.
