/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import io.flutter.ObservatoryConnector;
import io.flutter.devtools.DevToolsIdeFeature;
import io.flutter.jxbrowser.FailureType;
import io.flutter.jxbrowser.InstallationFailedReason;
import io.flutter.jxbrowser.JxBrowserManager;
import io.flutter.jxbrowser.JxBrowserStatus;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.JxBrowserUtils;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.TimeoutException;

import static io.flutter.view.FlutterView.*;
import static org.mockito.Mockito.*;

//@PrepareForTest({ThreadUtil.class, FlutterInitializer.class, JxBrowserUtils.class,
//  InspectorGroupManagerService.class, SwingUtilities.class})
public class FlutterViewTest {
  Project mockProject = mock(Project.class);
  @Mock FlutterApp mockApp;
  @Mock ToolWindow mockToolWindow;
  @Mock ObservatoryConnector mockObservatoryConnector;
  JxBrowserUtils mockUtils = mock(JxBrowserUtils.class);
  JxBrowserManager mockJxBrowserManager = mock(JxBrowserManager.class);

  @Test
  public void testHandleJxBrowserInstalled() {
    // If JxBrowser has been installed, we should use the DevTools instance to open the embedded browser.
    final FlutterView partialMockFlutterView = mock(FlutterView.class);
    doCallRealMethod().when(partialMockFlutterView).handleJxBrowserInstalled(mockApp, mockToolWindow, DevToolsIdeFeature.TOOL_WINDOW);

    partialMockFlutterView.handleJxBrowserInstalled(mockApp, mockToolWindow, DevToolsIdeFeature.TOOL_WINDOW);
    verify(partialMockFlutterView, times(1)).openInspectorWithDevTools(mockApp, mockToolWindow, true, DevToolsIdeFeature.TOOL_WINDOW);
    verify(partialMockFlutterView, times(1)).setUpToolWindowListener(mockApp, mockToolWindow, true, DevToolsIdeFeature.TOOL_WINDOW);
  }

  @Test
  public void testHandleJxBrowserInstallationFailed() {
    final JxBrowserUtils mockJxBrowserUtils = mock(JxBrowserUtils.class);
    when(mockJxBrowserUtils.licenseIsSet()).thenReturn(true);

    when(mockJxBrowserManager.getLatestFailureReason()).thenReturn(new InstallationFailedReason(FailureType.FILE_DOWNLOAD_FAILED));

    // If JxBrowser failed to install, we should show a failure message that allows the user to manually retry.
    final FlutterView flutterView = new FlutterView(mockProject, mockJxBrowserManager, mockJxBrowserUtils);
    final FlutterView spy = spy(flutterView);
    final ViewUtils utilsSpy = spy(flutterView.viewUtils);
    doNothing().when(utilsSpy).presentClickableLabel(
      eq(mockToolWindow),
      anyList()
    );

    spy.handleJxBrowserInstallationFailed(mockApp, mockToolWindow, DevToolsIdeFeature.TOOL_WINDOW);
    verify(utilsSpy, times(1)).presentClickableLabel(
      eq(mockToolWindow),
      anyList()
    );
  }

  @Test
  public void testHandleJxBrowserInstallationInProgressWithSuccessfulInstall() {
    when(mockJxBrowserManager.getStatus()).thenReturn(JxBrowserStatus.INSTALLED);

    // If the JxBrowser installation is initially in progress, we should show a message about the installation.
    // If the installation quickly finishes (on the first re-check), then we should call the function to handle successful installation.
    final FlutterView flutterView = new FlutterView(mockProject, mockJxBrowserManager, mockUtils);
    final FlutterView spy = spy(flutterView);

    doNothing().when(spy).presentOpenDevToolsOptionWithMessage(any(), any(), any(), any());
    doNothing().when(spy).handleJxBrowserInstalled(any(), any(), any());

    spy.handleJxBrowserInstallationInProgress(mockApp, mockToolWindow, DevToolsIdeFeature.TOOL_WINDOW);
    verify(spy, times(1))
      .presentOpenDevToolsOptionWithMessage(mockApp, mockToolWindow, INSTALLATION_IN_PROGRESS_LABEL, DevToolsIdeFeature.TOOL_WINDOW);
    verify(spy, times(1)).handleJxBrowserInstalled(mockApp, mockToolWindow, DevToolsIdeFeature.TOOL_WINDOW);
  }

  @Test
  public void testHandleJxBrowserInstallationInProgressWaiting() {
    when(mockJxBrowserManager.getStatus()).thenReturn(JxBrowserStatus.INSTALLATION_IN_PROGRESS);

    // If the JxBrowser installation is in progress and is not finished on the first re-check, we should start a thread to wait for the
    // installation to finish.
    final FlutterView flutterView = new FlutterView(mockProject, mockJxBrowserManager, mockUtils);
    final FlutterView spy = spy(flutterView);

    doNothing().when(spy).presentOpenDevToolsOptionWithMessage(any(), any(), any(), any());
    doNothing().when(spy).startJxBrowserInstallationWaitingThread(any(), any(), any());

    spy.handleJxBrowserInstallationInProgress(mockApp, mockToolWindow, DevToolsIdeFeature.TOOL_WINDOW);
    verify(spy, times(1))
      .presentOpenDevToolsOptionWithMessage(mockApp, mockToolWindow, INSTALLATION_IN_PROGRESS_LABEL, DevToolsIdeFeature.TOOL_WINDOW);
    verify(spy, times(1)).startJxBrowserInstallationWaitingThread(mockApp, mockToolWindow, DevToolsIdeFeature.TOOL_WINDOW);
  }

  @Test
  public void testWaitForJxBrowserInstallationWithoutTimeout() throws TimeoutException {
    when(mockJxBrowserManager.getStatus()).thenReturn(JxBrowserStatus.INSTALLATION_IN_PROGRESS);
    when(mockJxBrowserManager.waitForInstallation(INSTALLATION_WAIT_LIMIT_SECONDS)).thenReturn(JxBrowserStatus.INSTALLATION_FAILED);

    // If waiting for JxBrowser installation completes without timing out, then we should return to event thread.
    final FlutterView flutterView = new FlutterView(mockProject, mockJxBrowserManager, mockUtils);
    final FlutterView spy = spy(flutterView);

    doNothing().when(spy).handleUpdatedJxBrowserStatusOnEventThread(any(), any(), any(), any());

    spy.waitForJxBrowserInstallation(mockApp, mockToolWindow, DevToolsIdeFeature.TOOL_WINDOW);
    verify(spy, times(1))
      .handleUpdatedJxBrowserStatusOnEventThread(mockApp, mockToolWindow, JxBrowserStatus.INSTALLATION_FAILED, DevToolsIdeFeature.TOOL_WINDOW);
  }

  @Ignore
  @Test
  public void testWaitForJxBrowserInstallationWithTimeout() throws TimeoutException {
    when(mockJxBrowserManager.getStatus()).thenReturn(JxBrowserStatus.INSTALLATION_IN_PROGRESS);
    when(mockJxBrowserManager.waitForInstallation(INSTALLATION_WAIT_LIMIT_SECONDS)).thenThrow(new TimeoutException());

    // If the JxBrowser installation doesn't complete on time, we should show a timed out message.
    final FlutterView flutterView = new FlutterView(mockProject, mockJxBrowserManager, mockUtils);
    final FlutterView spy = spy(flutterView);

    doNothing().when(spy).presentOpenDevToolsOptionWithMessage(any(), any(), any(), any());

    spy.waitForJxBrowserInstallation(mockApp, mockToolWindow, DevToolsIdeFeature.TOOL_WINDOW);
    verify(spy, times(1))
      .presentOpenDevToolsOptionWithMessage(mockApp, mockToolWindow, INSTALLATION_TIMED_OUT_LABEL, DevToolsIdeFeature.TOOL_WINDOW);
  }

  @Test
  public void testHandleUpdatedJxBrowserStatusWithFailure() {
    // If waiting for JxBrowser installation completes with failure, then we should redirect to the function that handles failure.
    final FlutterView partialMockFlutterView = mock(FlutterView.class);
    doCallRealMethod().when(partialMockFlutterView)
      .handleUpdatedJxBrowserStatus(mockApp, mockToolWindow, JxBrowserStatus.INSTALLATION_FAILED, DevToolsIdeFeature.TOOL_WINDOW);
    partialMockFlutterView.handleUpdatedJxBrowserStatus(mockApp, mockToolWindow, JxBrowserStatus.INSTALLATION_FAILED, DevToolsIdeFeature.TOOL_WINDOW);
    verify(partialMockFlutterView, times(1)).handleJxBrowserInstallationFailed(mockApp, mockToolWindow, DevToolsIdeFeature.TOOL_WINDOW);
  }

  @Test
  public void testHandleUpdatedJxBrowserStatusWithSuccess() {
    // If waiting for JxBrowser installation completes with failure, then we should redirect to the function that handles failure.
    final FlutterView partialMockFlutterView = mock(FlutterView.class);
    doCallRealMethod().when(partialMockFlutterView)
      .handleUpdatedJxBrowserStatus(mockApp, mockToolWindow, JxBrowserStatus.INSTALLED, DevToolsIdeFeature.TOOL_WINDOW);
    partialMockFlutterView.handleUpdatedJxBrowserStatus(mockApp, mockToolWindow, JxBrowserStatus.INSTALLED, DevToolsIdeFeature.TOOL_WINDOW);
    verify(partialMockFlutterView, times(1)).handleJxBrowserInstalled(mockApp, mockToolWindow, DevToolsIdeFeature.TOOL_WINDOW);
  }

  @Test
  public void testHandleUpdatedJxBrowserStatusWithOtherstatus() {
    // If waiting for JxBrowser installation completes with any other status, then we should recommend opening non-embedded DevTools.
    final FlutterView partialMockFlutterView = mock(FlutterView.class);
    doCallRealMethod().when(partialMockFlutterView)
      .handleUpdatedJxBrowserStatus(mockApp, mockToolWindow, JxBrowserStatus.NOT_INSTALLED, DevToolsIdeFeature.TOOL_WINDOW);
    partialMockFlutterView.handleUpdatedJxBrowserStatus(mockApp, mockToolWindow, JxBrowserStatus.NOT_INSTALLED, DevToolsIdeFeature.TOOL_WINDOW);
    verify(partialMockFlutterView, times(1))
      .presentOpenDevToolsOptionWithMessage(mockApp, mockToolWindow, INSTALLATION_WAIT_FAILED, DevToolsIdeFeature.TOOL_WINDOW);
  }
}
