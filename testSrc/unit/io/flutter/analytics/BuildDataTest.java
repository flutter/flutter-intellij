/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.analytics;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.lang.dart.analytics.AnalyticsData;

import java.util.ArrayList;
import java.util.List;

public class BuildDataTest extends BasePlatformTestCase {

  public void testSuccessfulBuild() {
    final AnalyticsData event = assertOneElement(record(() -> Analytics.reportBuild(getProject(), "apk", 0, 61234)));

    assertSame(getProject(), event.getProject());
    assertEquals(AnalyticsConstants.BUILD_MENU, event.getId());
    assertEquals(AnalyticsConstants.BUILD_TYPE, event.getData().get("type"));
    assertEquals("apk", event.getData().get("buildTarget"));
    assertEquals(AnalyticsConstants.OUTCOME_SUCCESS, event.getData().get("outcome"));
    assertEquals(Integer.valueOf(0), event.getData().get("exitCode"));
    // Under the Dart plugin's shared duration_ms key.
    assertEquals(Integer.valueOf(61234), event.getData().get("duration_ms"));
  }

  public void testFailedBuild() {
    final AnalyticsData event = assertOneElement(record(() -> Analytics.reportBuild(getProject(), "appbundle", 1, 4321)));

    assertEquals(AnalyticsConstants.OUTCOME_ERROR, event.getData().get("outcome"));
    assertEquals(Integer.valueOf(1), event.getData().get("exitCode"));
  }

  public void testBuildThatDidNotStart() {
    final AnalyticsData event = assertOneElement(record(() -> Analytics.reportBuildNotStarted(getProject(), "web")));

    assertEquals(AnalyticsConstants.OUTCOME_NOT_STARTED, event.getData().get("outcome"));
    assertFalse(event.getData().containsKey("exitCode"));
    assertFalse(event.getData().containsKey("duration_ms"));
  }

  private static List<AnalyticsData> record(Runnable action) {
    final List<AnalyticsData> events = new ArrayList<>();
    com.jetbrains.lang.dart.analytics.Analytics.withReportObserver(events::add, action);
    return events;
  }
}
