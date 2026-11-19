/*
 * Copyright 2025 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

package io.flutter.analytics

import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.openapi.project.Project
import com.jetbrains.lang.dart.analytics.Analytics as DartAnalytics
import com.jetbrains.lang.dart.analytics.AnalyticsConstants as DartAnalyticsConstants
import com.jetbrains.lang.dart.analytics.AnalyticsData
import com.jetbrains.lang.dart.analytics.BooleanValue
import com.jetbrains.lang.dart.analytics.IntValue
import com.jetbrains.lang.dart.analytics.StringValue

/**
 * Defines standard keys for analytics data properties.
 *
 * The properties are exposed as `@JvmField`s to be easily accessible as static
 * fields from Java.
 */
object AnalyticsConstants {
  /**
   * Indicates if the project is a Google3 project.
   */
  @JvmField
  val GOOGLE3 = BooleanValue("google3")

  /**
   * Indicates if the Flutter SDK is missing.
   */
  @JvmField
  val MISSING_SDK = BooleanValue("missingSdk")

  /**
   * Indicates if a restart is required for a hot reload request.
   */
  @JvmField
  val REQUIRES_RESTART = BooleanValue("requiresRestart")

  const val MECHANISM_FLUTTER_ATTACH = "flutter_attach"
  const val MECHANISM_FLUTTER_APP = "flutter_app"
  const val MECHANISM_FLUTTER_TESTS = "flutter_tests"

  const val DEBUG_SESSION_TYPE = "debug_session"
  const val RUN_SESSION_TYPE = "run_session"

  /** Event type and id for one reload-on-save attempt. */
  const val HOT_RELOAD_TYPE = "hot_reload"
  const val RELOAD_ON_SAVE_ID = "reload_on_save"

  /** Reload-on-save event fields. */
  val TRIGGER = StringValue("trigger")
  val OUTCOME = StringValue("outcome")
  val ERROR_CODE = IntValue("errorCode")

  /** Reload-on-save triggers. */
  const val TRIGGER_SAVE_ACTION = "save_action"
  const val TRIGGER_DOCUMENT_SAVE = "document_save"

  /** Reload-on-save outcomes. */
  const val OUTCOME_SUCCESS = "success"
  const val OUTCOME_ERROR = "error"
  const val OUTCOME_EXCEPTION = "exception"
  const val OUTCOME_SKIPPED_NOT_CONNECTED = "skipped_not_connected"
  const val OUTCOME_SKIPPED_SYNTAX_ERROR_CURRENT_FILE = "skipped_syntax_error_current_file"
  const val OUTCOME_SKIPPED_SYNTAX_ERROR_ANY_EDITOR = "skipped_syntax_error_any_editor"

  /** Widget Preview start event type and ids. */
  const val WIDGET_PREVIEW_TYPE = "widget_preview"
  const val WIDGET_PREVIEW_STARTED = "started"
  const val WIDGET_PREVIEW_FAILED = "failed"

  /** Why Widget Preview failed to start, or why an embedded browser panel failed to open; a `REASON_*` value. */
  val REASON = StringValue("reason")

  /** Widget Preview failure reasons. */
  const val REASON_NO_SDK = "no_sdk"
  const val REASON_SDK_UNKNOWN_VERSION = "sdk_unknown_version"
  const val REASON_SDK_TOO_OLD = "sdk_too_old"
  const val REASON_NO_PUB_ROOT = "no_pub_root"
  const val REASON_PROCESS_ERROR = "process_error"
  const val REASON_NO_URL = "no_url"
  const val REASON_EXEC_FAILED = "exec_failed"

  /** Embedded browser panel event type and ids: one per `EmbeddedBrowser.openPanel` call. */
  const val EMBEDDED_BROWSER_TYPE = "embedded_browser"
  const val EMBEDDED_BROWSER_OPENED = "opened"
  const val EMBEDDED_BROWSER_FAILED = "failed"

  /** Embedded browser fields: the engine, the tool window id, and whether the call created the tab. */
  val ENGINE = StringValue("engine")
  val FEATURE = StringValue("feature")
  val NEW_TAB = BooleanValue("newTab")

  /** Embedded browser engines. */
  const val ENGINE_JCEF = "jcef"
  const val ENGINE_JXBROWSER = "jxbrowser"

  /** Why an embedded browser panel failed to open. */
  const val REASON_TAB_CREATE_FAILED = "tab_create_failed"
  const val REASON_ENGINE_NOT_READY = "engine_not_ready"
  const val REASON_LOAD_FAILED = "load_failed"
}

private class SessionData(type: String, mechanism: String, project: Project?) :
  AnalyticsData(type, mechanism, project)

private class ReloadOnSaveData(project: Project?, trigger: String, outcome: String, durationMs: Int?, errorCode: Int?) :
  AnalyticsData(AnalyticsConstants.HOT_RELOAD_TYPE, AnalyticsConstants.RELOAD_ON_SAVE_ID, project) {
  init {
    add(AnalyticsConstants.TRIGGER, trigger)
    add(AnalyticsConstants.OUTCOME, outcome)
    durationMs?.let { add(DartAnalyticsConstants.DURATION_MS, it) }
    // Never the daemon's message, which can contain user code.
    errorCode?.let { add(AnalyticsConstants.ERROR_CODE, it) }
  }
}

private class WidgetPreviewData(project: Project?, id: String, reason: String?) :
  AnalyticsData(AnalyticsConstants.WIDGET_PREVIEW_TYPE, id, project) {
  init {
    // Only the reason, never an exception message (it can contain VM service tokens or local paths).
    reason?.let { add(AnalyticsConstants.REASON, it) }
  }
}

private class EmbeddedBrowserData(project: Project?, id: String, engine: String, feature: String, newTab: Boolean, reason: String?) :
  AnalyticsData(AnalyticsConstants.EMBEDDED_BROWSER_TYPE, id, project) {
  init {
    add(AnalyticsConstants.ENGINE, engine)
    add(AnalyticsConstants.FEATURE, feature)
    add(AnalyticsConstants.NEW_TAB, newTab)
    // Only the reason, never an exception message (it can contain VM service tokens or local paths).
    reason?.let { add(AnalyticsConstants.REASON, it) }
  }
}

object Analytics {
  @JvmStatic
  fun report(data: AnalyticsData) {
    try {
      DartAnalytics.report(data)
    } catch (t: Throwable) {
      // Fail-safe: analytics should never crash the IDE or run/debug sessions.
    }
  }

  @JvmStatic
  fun recordRunOrDebugSession(mechanism: String, executor: Executor, project: Project?) {
    val type = if (executor.id == DefaultDebugExecutor.EXECUTOR_ID) {
      AnalyticsConstants.DEBUG_SESSION_TYPE
    } else {
      AnalyticsConstants.RUN_SESSION_TYPE
    }
    report(SessionData(type, mechanism, project))
  }

  @JvmStatic
  fun reportReloadOnSave(project: Project?, trigger: String, outcome: String, durationMs: Int?, errorCode: Int?) {
    report(ReloadOnSaveData(project, trigger, outcome, durationMs, errorCode))
  }

  @JvmStatic
  fun reportWidgetPreview(project: Project?, id: String, reason: String?) {
    report(WidgetPreviewData(project, id, reason))
  }

  @JvmStatic
  fun reportEmbeddedBrowser(project: Project?, id: String, engine: String, feature: String, newTab: Boolean, reason: String?) {
    report(EmbeddedBrowserData(project, id, engine, feature, newTab, reason))
  }
}
