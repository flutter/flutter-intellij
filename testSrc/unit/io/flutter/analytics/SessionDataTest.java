/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.analytics;

import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.lang.dart.analytics.AnalyticsData;
import io.flutter.run.FlutterLaunchMode;
import io.flutter.run.LaunchState;

import java.util.ArrayList;
import java.util.List;

public class SessionDataTest extends BasePlatformTestCase {
  private static final String[] NO_ARGS = new String[0];

  public void testRunSessionShape() {
    final AnalyticsData event = assertOneElement(record(() -> Analytics.recordRunOrDebugSession(
      AnalyticsConstants.MECHANISM_FLUTTER_APP, DefaultRunExecutor.getRunExecutorInstance(), getProject(),
      AnalyticsConstants.BUILD_MODE_RELEASE)));

    // Events without a project are dropped downstream, after the observer sees them.
    assertSame(getProject(), event.getProject());
    assertEquals(AnalyticsConstants.MECHANISM_FLUTTER_APP, event.getId());
    assertEquals(AnalyticsConstants.RUN_SESSION_TYPE, event.getData().get("type"));
    assertEquals(AnalyticsConstants.EXECUTOR_RUN, event.getData().get("executor"));
    assertEquals(AnalyticsConstants.BUILD_MODE_RELEASE, event.getData().get("buildMode"));
  }

  public void testDebugSessionShape() {
    final AnalyticsData event = assertOneElement(record(() -> Analytics.recordRunOrDebugSession(
      AnalyticsConstants.MECHANISM_FLUTTER_ATTACH, DefaultDebugExecutor.getDebugExecutorInstance(), getProject(),
      AnalyticsConstants.BUILD_MODE_DEBUG)));

    assertSame(getProject(), event.getProject());
    assertEquals(AnalyticsConstants.MECHANISM_FLUTTER_ATTACH, event.getId());
    assertEquals(AnalyticsConstants.DEBUG_SESSION_TYPE, event.getData().get("type"));
    assertEquals(AnalyticsConstants.EXECUTOR_DEBUG, event.getData().get("executor"));
    assertEquals(AnalyticsConstants.BUILD_MODE_DEBUG, event.getData().get("buildMode"));
  }

  public void testTestSessionOmitsBuildMode() {
    final AnalyticsData event = assertOneElement(record(() -> Analytics.recordRunOrDebugSession(
      AnalyticsConstants.MECHANISM_FLUTTER_TESTS, DefaultRunExecutor.getRunExecutorInstance(), getProject())));

    assertSame(getProject(), event.getProject());
    assertEquals(AnalyticsConstants.MECHANISM_FLUTTER_TESTS, event.getId());
    assertEquals(AnalyticsConstants.RUN_SESSION_TYPE, event.getData().get("type"));
    assertEquals(AnalyticsConstants.EXECUTOR_RUN, event.getData().get("executor"));
    assertFalse(event.getData().containsKey("buildMode"));
  }

  public void testExecutorFor() {
    assertEquals(AnalyticsConstants.EXECUTOR_RUN, Analytics.executorFor(DefaultRunExecutor.EXECUTOR_ID));
    assertEquals(AnalyticsConstants.EXECUTOR_DEBUG, Analytics.executorFor(DefaultDebugExecutor.EXECUTOR_ID));
    assertEquals(AnalyticsConstants.EXECUTOR_COVERAGE, Analytics.executorFor("Coverage"));
    assertEquals(AnalyticsConstants.EXECUTOR_PROFILER, Analytics.executorFor(LaunchState.ANDROID_PROFILER_EXECUTOR_ID));
    // Never the raw id.
    assertEquals(AnalyticsConstants.EXECUTOR_OTHER, Analytics.executorFor("SomeThirdPartyExecutor"));
    assertEquals(AnalyticsConstants.EXECUTOR_OTHER, Analytics.executorFor(null));
  }

  public void testBuildModeFromEnvironment() {
    assertEquals(AnalyticsConstants.BUILD_MODE_DEBUG,
                 Analytics.buildModeFor(FlutterLaunchMode.DEBUG, DefaultRunExecutor.EXECUTOR_ID, NO_ARGS));
    assertEquals(AnalyticsConstants.BUILD_MODE_PROFILE,
                 Analytics.buildModeFor(FlutterLaunchMode.PROFILE, DefaultRunExecutor.EXECUTOR_ID, NO_ARGS));
    assertEquals(AnalyticsConstants.BUILD_MODE_RELEASE,
                 Analytics.buildModeFor(FlutterLaunchMode.RELEASE, DefaultRunExecutor.EXECUTOR_ID, NO_ARGS));
  }

  public void testBuildModeForProfilerExecutor() {
    // The profiler executor only switches the environment to profile later, in LaunchState.launch.
    assertEquals(AnalyticsConstants.BUILD_MODE_PROFILE,
                 Analytics.buildModeFor(FlutterLaunchMode.DEBUG, LaunchState.ANDROID_PROFILER_EXECUTOR_ID, NO_ARGS));
  }

  public void testBuildModeFromExtraArgs() {
    assertEquals(AnalyticsConstants.BUILD_MODE_RELEASE,
                 Analytics.buildModeFor(FlutterLaunchMode.DEBUG, DefaultRunExecutor.EXECUTOR_ID,
                                         new String[]{"--dart-define=X=1", "--release"}));
    assertEquals(AnalyticsConstants.BUILD_MODE_PROFILE,
                 Analytics.buildModeFor(FlutterLaunchMode.DEBUG, DefaultDebugExecutor.EXECUTOR_ID, new String[]{"--profile"}));
    // Only exact flags count.
    assertEquals(AnalyticsConstants.BUILD_MODE_DEBUG,
                 Analytics.buildModeFor(FlutterLaunchMode.DEBUG, DefaultRunExecutor.EXECUTOR_ID,
                                         new String[]{"--dart-define=MODE=--release"}));
  }

  private static List<AnalyticsData> record(Runnable action) {
    final List<AnalyticsData> events = new ArrayList<>();
    com.jetbrains.lang.dart.analytics.Analytics.withReportObserver(events::add, action);
    return events;
  }
}
