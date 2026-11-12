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
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.lang.dart.analytics.Analytics;
import com.jetbrains.lang.dart.analytics.AnalyticsData;
import io.flutter.FlutterConstants;
import io.flutter.run.FlutterReloadManager;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JPanel;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Checks the analytics events sent for reload, restart, and their toolbar/menu retarget actions.
 */
public class FlutterAppActionAnalyticsTest extends BasePlatformTestCase {

  private FlutterReloadManager reloadManager;
  private FlutterApp app;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    reloadManager = mock(FlutterReloadManager.class);
    ServiceContainerUtil.replaceService(getProject(), FlutterReloadManager.class, reloadManager, getTestRootDisposable());
    app = mock(FlutterApp.class);
    when(app.getProject()).thenReturn(getProject());
    // A connected app's actions register themselves in ProjectActions, where retarget actions find them.
    when(app.isConnected()).thenReturn(true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      for (String id : List.of(ReloadFlutterApp.ID, RestartFlutterApp.ID, ReloadAllFlutterApps.ID, RestartAllFlutterApps.ID)) {
        ProjectActions.unregisterAction(getProject(), id);
      }
    }
    finally {
      super.tearDown();
    }
  }

  public void testReloadReportsOnce() {
    final AnalyticsData event = assertOneElement(record(new ReloadFlutterApp(app, () -> true), "RunnerToolbar", null));

    verify(reloadManager).saveAllAndReload(app, FlutterConstants.RELOAD_REASON_MANUAL);
    assertEquals(ReloadFlutterApp.ID, event.getId());
    assertEquals("action", event.getData().get("type"));
    assertEquals("RunnerToolbar", event.getData().get("place"));
    assertEquals(Boolean.FALSE, event.getData().get("requiresRestart"));
  }

  public void testShiftClickReloadRestartsAndReportsRequiresRestart() {
    final AnalyticsData event = assertOneElement(record(new ReloadFlutterApp(app, () -> true), "RunnerToolbar", shiftClick()));

    verify(reloadManager).saveAllAndRestart(app, FlutterConstants.RELOAD_REASON_MANUAL);
    verify(reloadManager, never()).saveAllAndReload(any(), anyString());
    assertEquals(ReloadFlutterApp.ID, event.getId());
    assertEquals(Boolean.TRUE, event.getData().get("requiresRestart"));
  }

  public void testShiftFromGoToActionStillReloads() {
    // From Find Action, the modifiers belong to the shortcut that opened the dialog.
    final AnalyticsData event = assertOneElement(record(new ReloadFlutterApp(app, () -> true), "GoToAction", shiftClick()));

    verify(reloadManager).saveAllAndReload(app, FlutterConstants.RELOAD_REASON_MANUAL);
    assertEquals(Boolean.FALSE, event.getData().get("requiresRestart"));
  }

  public void testRestartReportsOnce() {
    final AnalyticsData event = assertOneElement(record(new RestartFlutterApp(app, () -> true), "DebuggerToolbar", null));

    verify(reloadManager).saveAllAndRestart(app, FlutterConstants.RELOAD_REASON_MANUAL);
    assertEquals(RestartFlutterApp.ID, event.getId());
    assertEquals("DebuggerToolbar", event.getData().get("place"));
  }

  public void testRetargetedReloadReportsOnceWithTargetIdAndToolbarPlace() {
    new ReloadFlutterApp(app, () -> true); // Registers itself as the retarget's target.

    final AnalyticsData event = assertOneElement(record(new ReloadFlutterAppRetarget(), ActionPlaces.MAIN_TOOLBAR, null));

    assertEquals(ReloadFlutterApp.ID, event.getId());
    assertEquals(ActionPlaces.MAIN_TOOLBAR, event.getData().get("place"));
  }

  public void testRetargetedRestartReportsOnceWithTargetIdAndMenuPlace() {
    new RestartFlutterApp(app, () -> true);

    final AnalyticsData event = assertOneElement(record(new RestartFlutterAppRetarget(), ActionPlaces.MAIN_MENU, null));

    assertEquals(RestartFlutterApp.ID, event.getId());
    assertEquals(ActionPlaces.MAIN_MENU, event.getData().get("place"));
  }

  public void testRetargetedAllActionsReportOnce() {
    // Stand-ins for ReloadAllFlutterApps / RestartAllFlutterApps, which report with their own ids.
    ProjectActions.registerAction(getProject(), ReloadAllFlutterApps.ID, new ReportingAction(ReloadAllFlutterApps.ID));
    ProjectActions.registerAction(getProject(), RestartAllFlutterApps.ID, new ReportingAction(RestartAllFlutterApps.ID));

    assertEquals(ReloadAllFlutterApps.ID,
                 assertOneElement(record(new ReloadAllFlutterAppsRetarget(), ActionPlaces.MAIN_MENU, null)).getId());
    assertEquals(RestartAllFlutterApps.ID,
                 assertOneElement(record(new RestartAllFlutterAppsRetarget(), ActionPlaces.MAIN_MENU, null)).getId());
  }

  public void testRetargetWithoutTargetReportsNothing() {
    // No app is running, so nothing is registered in ProjectActions.
    assertEmpty(record(new ReloadFlutterAppRetarget(), ActionPlaces.MAIN_TOOLBAR, null));
    assertEmpty(record(new RestartFlutterAppRetarget(), ActionPlaces.MAIN_TOOLBAR, null));
  }

  public void testOpenActionIdsAreSpelledCorrectly() {
    assertEquals("flutter.open.emulator", OpenEmulatorAction.ID);
    assertEquals("flutter.open.android.module", OpenAndroidModule.ID);
    assertEquals("flutter.open.android.studio", OpenInAndroidStudioAction.ID);
  }

  private @NotNull List<AnalyticsData> record(@NotNull AnAction action, @NotNull String place, @Nullable InputEvent inputEvent) {
    final DataContext context = dataId -> CommonDataKeys.PROJECT.is(dataId) ? getProject() : null;
    final AnActionEvent event = AnActionEvent.createEvent(action, context, null, place, ActionUiKind.NONE, inputEvent);
    final List<AnalyticsData> events = new ArrayList<>();
    Analytics.withReportObserver(events::add, () -> action.actionPerformed(event));
    return events;
  }

  private static @NotNull InputEvent shiftClick() {
    return new MouseEvent(new JPanel(), MouseEvent.MOUSE_CLICKED, 0, InputEvent.SHIFT_DOWN_MASK, 0, 0, 1, false);
  }

  /** A stand-in target action that reports an event with the given id. */
  private static final class ReportingAction extends AnAction {
    private final String reportedId;

    ReportingAction(@NotNull String reportedId) {
      this.reportedId = reportedId;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Analytics.report(AnalyticsData.forAction(reportedId, e.getPlace(), e.getProject()));
    }
  }
}
