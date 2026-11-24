/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.jetbrains.lang.dart.analytics.Analytics;
import com.jetbrains.lang.dart.analytics.AnalyticsData;
import io.flutter.jxbrowser.EmbeddedJxBrowser;
import io.flutter.run.daemon.DeviceService;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unregistered instances of registered actions (created by tool windows and the device selector) report with the
 * registered action id, instead of a null id that the reporter drops.
 */
public class UnregisteredActionAnalyticsTest extends BasePlatformTestCase {

  public void testIdsMatchPluginXml() {
    assertInstanceOf(ActionManager.getInstance().getAction(RefreshToolWindowAction.ID), RefreshToolWindowAction.class);
    assertInstanceOf(ActionManager.getInstance().getAction(RestartFlutterDaemonAction.ID), RestartFlutterDaemonAction.class);
  }

  public void testToolWindowRefreshButtonReports() {
    // Keep the refresh itself from creating a real embedded browser.
    ServiceContainerUtil.replaceService(getProject(), EmbeddedJxBrowser.class, mock(EmbeddedJxBrowser.class), getTestRootDisposable());

    final AnalyticsData event = assertOneElement(record(new RefreshToolWindowAction("Flutter Inspector"), "ToolwindowTitle"));

    assertEquals(RefreshToolWindowAction.ID, event.getId());
    assertEquals("ToolwindowTitle", event.getData().get("place"));
  }

  public void testDeviceSelectorDaemonRestartReports() {
    final DeviceService deviceService = mock(DeviceService.class);
    ServiceContainerUtil.replaceService(getProject(), DeviceService.class, deviceService, getTestRootDisposable());

    final AnalyticsData event = assertOneElement(record(RestartFlutterDaemonAction.forDeviceSelector(), "popup"));

    verify(deviceService).restart();
    assertEquals(RestartFlutterDaemonAction.ID, event.getId());
  }

  private @NotNull List<AnalyticsData> record(@NotNull AnAction action, @NotNull String place) {
    final DataContext context = dataId -> CommonDataKeys.PROJECT.is(dataId) ? getProject() : null;
    final AnActionEvent event = AnActionEvent.createEvent(action, context, null, place, ActionUiKind.NONE, null);
    final List<AnalyticsData> events = new ArrayList<>();
    Analytics.withReportObserver(events::add, () -> action.actionPerformed(event));
    return events;
  }
}
