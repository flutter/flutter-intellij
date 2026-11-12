/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.lang.dart.analytics.Analytics;
import com.jetbrains.lang.dart.analytics.AnalyticsData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Checks the analytics events sent for reload, restart, and their toolbar/menu retarget actions.
 */
public class FlutterAppActionAnalyticsTest extends BasePlatformTestCase {

  @Override
  protected void tearDown() throws Exception {
    try {
      ProjectActions.unregisterAction(getProject(), ReloadFlutterApp.ID);
      ProjectActions.unregisterAction(getProject(), ReloadAllFlutterApps.ID);
    }
    finally {
      super.tearDown();
    }
  }

  public void testReloadEventHasIdAndRequiresRestart() {
    final Map<String, Object> data = ReloadFlutterApp.createAnalyticsData("RunnerToolbar", getProject(), true).getData();

    assertEquals(ReloadFlutterApp.ID, data.get("id"));
    assertEquals("action", data.get("type"));
    assertEquals("RunnerToolbar", data.get("place"));
    assertEquals(Boolean.TRUE, data.get("requiresRestart"));
  }

  public void testRestartEventHasId() {
    final AnalyticsData event = RestartFlutterApp.createAnalyticsData(ActionPlaces.MAIN_TOOLBAR, getProject());

    assertEquals(RestartFlutterApp.ID, event.getId());
    assertEquals(ActionPlaces.MAIN_TOOLBAR, event.getData().get("place"));
  }

  public void testRetargetDelegatesWithoutReportingItself() {
    final RecordingAction target = new RecordingAction(null);
    ProjectActions.registerAction(getProject(), ReloadFlutterApp.ID, target);

    final List<AnalyticsData> events = record(new ReloadFlutterAppRetarget());

    assertEquals(1, target.invocations);
    assertEmpty(events);
  }

  public void testRetargetedAllActionReportsOnce() {
    // Reload All reports its own event, like the real ReloadAllFlutterApps.
    final RecordingAction target = new RecordingAction(ReloadAllFlutterApps.ID);
    ProjectActions.registerAction(getProject(), ReloadAllFlutterApps.ID, target);

    final List<AnalyticsData> events = record(new ReloadAllFlutterAppsRetarget());

    assertEquals(1, target.invocations);
    final AnalyticsData event = assertOneElement(events);
    assertEquals(ReloadAllFlutterApps.ID, event.getId());
    assertEquals(ActionPlaces.MAIN_TOOLBAR, event.getData().get("place"));
  }

  public void testOpenActionIdsAreSpelledCorrectly() {
    assertEquals("flutter.open.emulator", OpenEmulatorAction.ID);
    assertEquals("flutter.open.android.module", OpenAndroidModule.ID);
    assertEquals("flutter.open.android.studio", OpenInAndroidStudioAction.ID);
  }

  private @NotNull List<AnalyticsData> record(@NotNull AnAction retarget) {
    final AnActionEvent event = AnActionEvent.createEvent(
      retarget, SimpleDataContext.getProjectContext(getProject()), null, ActionPlaces.MAIN_TOOLBAR, ActionUiKind.NONE, null);
    final List<AnalyticsData> events = new ArrayList<>();
    Analytics.withReportObserver(events::add, () -> retarget.actionPerformed(event));
    return events;
  }

  /** A stand-in target action that counts invocations and optionally reports an event with the given id. */
  private static final class RecordingAction extends AnAction {
    private final String reportedId;
    int invocations;

    RecordingAction(String reportedId) {
      this.reportedId = reportedId;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      invocations++;
      if (reportedId != null) {
        Analytics.report(AnalyticsData.forAction(reportedId, e.getPlace(), e.getProject()));
      }
    }
  }
}
