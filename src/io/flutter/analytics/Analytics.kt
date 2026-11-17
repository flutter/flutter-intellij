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

  /** What started a reload-on-save attempt; one of the `TRIGGER_*` values. */
  @JvmField
  val TRIGGER = StringValue("trigger")

  /** How a reload-on-save attempt ended; one of the `OUTCOME_*` values. */
  @JvmField
  val OUTCOME = StringValue("outcome")

  /** The daemon's `RestartResult` code for a failed reload. Never the message, which can contain user code. */
  @JvmField
  val ERROR_CODE = IntValue("errorCode")

  /**
   * An explicit Save All action (for example, Cmd/Ctrl+S).
   */
  const val TRIGGER_SAVE_ACTION = "save_action"

  /**
   * Any other save of all documents: idle or frame-deactivation autosave, and saves made by other IDE features
   * (running a configuration, VCS operations, and so on).
   */
  const val TRIGGER_DOCUMENT_SAVE = "document_save"

  /** The reload completed and flutter_tools reported success. */
  const val OUTCOME_SUCCESS = "success"

  /**
   * flutter_tools answered with a failed result. `errorCode` carries its code, which is 1 for almost every failure,
   * so use flutter_tools' own `hotRunnerInfo` labels for failure reasons.
   */
  const val OUTCOME_ERROR = "error"

  /**
   * No result arrived: the daemon answered `app.restart` with an error response, or the reload couldn't be sent
   * (for example, the app id wasn't set yet).
   */
  const val OUTCOME_EXCEPTION = "exception"

  /** The app started shutting down before the reload was sent. */
  const val OUTCOME_SKIPPED_NOT_CONNECTED = "skipped_not_connected"

  /** The saved file has a syntax (parse) error; the user sees a "Reload not performed" notification. */
  const val OUTCOME_SKIPPED_SYNTAX_ERROR_CURRENT_FILE = "skipped_syntax_error_current_file"

  /**
   * Some open editor in the project (of any file type) has a syntax error; skipped silently. Only the document-save
   * path checks this, and it's reported at most once a minute because idle autosave can repeat it.
   */
  const val OUTCOME_SKIPPED_SYNTAX_ERROR_ANY_EDITOR = "skipped_syntax_error_any_editor"
}

private class SessionData(type: String, mechanism: String, project: Project?) :
  AnalyticsData(type, mechanism, project)

/**
 * One reload-on-save attempt, as seen by the IDE.
 *
 * `flutter_tools` separately reports `hotRunnerInfo` (with `reason=save`, outcome label, and timings) for the
 * attempts that reach it. This event adds what only the IDE knows: the trigger, the attempts the IDE skipped, and
 * failures where no daemon result arrived. Don't sum the two.
 */
class ReloadOnSaveData(
  project: Project?,
  trigger: String,
  outcome: String,
  durationMs: Long? = null,
  errorCode: Int? = null,
) : AnalyticsData(AnalyticsConstants.HOT_RELOAD_TYPE, AnalyticsConstants.RELOAD_ON_SAVE_ID, project) {
  init {
    add(AnalyticsConstants.TRIGGER, trigger)
    add(AnalyticsConstants.OUTCOME, outcome)
    // Reuses the Dart plugin's `duration_ms` key, so both plugins' durations land in one GA4 metric. The Dart reporter
    // only serializes String, Boolean, and Int values and silently drops a Long, hence the clamp.
    durationMs?.let { add(DartAnalyticsConstants.DURATION_MS, it.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()) }
    errorCode?.let { add(AnalyticsConstants.ERROR_CODE, it) }
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
  @JvmOverloads
  fun reportReloadOnSave(
    project: Project?,
    trigger: String,
    outcome: String,
    durationMs: Long? = null,
    errorCode: Int? = null,
  ) {
    report(ReloadOnSaveData(project, trigger, outcome, durationMs, errorCode))
  }
}
