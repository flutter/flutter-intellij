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

public class WidgetPreviewDataTest extends BasePlatformTestCase {

  public void testStartedHasNoReason() {
    final AnalyticsData event = assertOneElement(record(() -> Analytics.reportWidgetPreview(
      getProject(), AnalyticsConstants.WIDGET_PREVIEW_STARTED, null)));

    assertEquals(AnalyticsConstants.WIDGET_PREVIEW_STARTED, event.getId());
    assertEquals(AnalyticsConstants.WIDGET_PREVIEW_TYPE, event.getData().get("type"));
    assertFalse(event.getData().containsKey("reason"));
  }

  public void testFailedCarriesReason() {
    final AnalyticsData event = assertOneElement(record(() -> Analytics.reportWidgetPreview(
      getProject(), AnalyticsConstants.WIDGET_PREVIEW_FAILED, AnalyticsConstants.REASON_SDK_TOO_OLD)));

    assertEquals(AnalyticsConstants.WIDGET_PREVIEW_FAILED, event.getId());
    assertEquals(AnalyticsConstants.REASON_SDK_TOO_OLD, event.getData().get("reason"));
  }

  private static List<AnalyticsData> record(Runnable action) {
    final List<AnalyticsData> events = new ArrayList<>();
    com.jetbrains.lang.dart.analytics.Analytics.withReportObserver(events::add, action);
    return events;
  }
}
