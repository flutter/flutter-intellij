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

public class ReloadOnSaveDataTest extends BasePlatformTestCase {

  public void testEventShape() {
    final AnalyticsData event = assertOneElement(record(() -> Analytics.reportReloadOnSave(
      getProject(), AnalyticsConstants.TRIGGER_SAVE_ACTION, AnalyticsConstants.OUTCOME_ERROR, 1234, 3)));

    assertEquals(AnalyticsConstants.RELOAD_ON_SAVE_ID, event.getId());
    assertEquals(AnalyticsConstants.HOT_RELOAD_TYPE, event.getData().get("type"));
    assertEquals(AnalyticsConstants.TRIGGER_SAVE_ACTION, event.getData().get("trigger"));
    assertEquals(AnalyticsConstants.OUTCOME_ERROR, event.getData().get("outcome"));
    // Under the Dart plugin's shared duration_ms key.
    assertEquals(Integer.valueOf(1234), event.getData().get("duration_ms"));
    assertEquals(Integer.valueOf(3), event.getData().get("errorCode"));
  }

  public void testSkipOmitsDurationAndCode() {
    final AnalyticsData event = assertOneElement(record(() -> Analytics.reportReloadOnSave(
      getProject(), AnalyticsConstants.TRIGGER_DOCUMENT_SAVE, AnalyticsConstants.OUTCOME_SKIPPED_SYNTAX_ERROR_CURRENT_FILE, null, null)));

    assertFalse(event.getData().containsKey("duration_ms"));
    assertFalse(event.getData().containsKey("errorCode"));
  }

  private static List<AnalyticsData> record(Runnable action) {
    final List<AnalyticsData> events = new ArrayList<>();
    com.jetbrains.lang.dart.analytics.Analytics.withReportObserver(events::add, action);
    return events;
  }
}
