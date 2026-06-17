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
import com.jetbrains.lang.dart.analytics.AnalyticsData
import com.jetbrains.lang.dart.analytics.BooleanValue

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
   * Indicates if the project is in a Bazel context.
   */
  @JvmField
  val IN_BAZEL_CONTEXT = BooleanValue("inBazelContext")

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
  const val MECHANISM_BAZEL_APP = "bazel_app"
  const val MECHANISM_BAZEL_TEST = "bazel_test"
  const val MECHANISM_FLUTTER_TESTS = "flutter_tests"

  const val DEBUG_SESSION_TYPE = "debug_session"
  const val RUN_SESSION_TYPE = "run_session"
}

private class SessionData(type: String, mechanism: String, project: Project?) :
  AnalyticsData(type, mechanism, project)

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
}
