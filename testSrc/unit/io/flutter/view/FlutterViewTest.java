/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.components.labels.LinkListener;
import io.flutter.ObservatoryConnector;
import io.flutter.devtools.DevToolsManager;
import io.flutter.inspector.InspectorService;
import io.flutter.jxbrowser.JxBrowserManager;
import io.flutter.jxbrowser.JxBrowserStatus;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.ThreadUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static io.flutter.view.FlutterView.*;
import static org.mockito.Mockito.*;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({DevToolsManager.class, JxBrowserManager.class, ThreadUtil.class})
public class FlutterViewTest {
  @Mock Project mockProject;
  @Mock FlutterApp mockApp;
  @Mock InspectorService mockInspectorService;
  @Mock ToolWindow mockToolWindow;
  @Mock DevToolsManager mockDevToolsManager;
  @Mock ObservatoryConnector mockObservatoryConnector;
  @Mock JxBrowserManager mockJxBrowserManager;

  @Test
  public void testHandleJxBrowserInstalled() {
    final String testUrl = "http://www.testUrl.com";
    final String projectName = "Test Project Name";
    final FlutterView flutterView = new FlutterView(mockProject);

    PowerMockito.mockStatic(DevToolsManager.class);
    when(DevToolsManager.getInstance(mockProject)).thenReturn(mockDevToolsManager);
    when(mockDevToolsManager.hasInstalledDevTools()).thenReturn(true);
    when(mockApp.getConnector()).thenReturn(mockObservatoryConnector);
    when(mockObservatoryConnector.getBrowserUrl()).thenReturn(testUrl);
    when(mockToolWindow.getContentManager()).thenReturn(null);
    when(mockApp.device()).thenReturn(null);
    when(mockApp.getProject()).thenReturn(mockProject);
    when(mockProject.getName()).thenReturn(projectName);

    flutterView.handleJxBrowserInstalled(mockApp, mockInspectorService, mockToolWindow);
    verify(mockDevToolsManager, times(1)).openBrowserIntoPanel(testUrl, null, projectName, "inspector");
  }

  @Test
  public void testHandleJxBrowserInstalledWithDevtoolsSucceeded() {
    final String testUrl = "http://www.testUrl.com";
    final String projectName = "Test Project Name";

    final FlutterView partialMockFlutterView = mock(FlutterView.class);
    doCallRealMethod().when(partialMockFlutterView).handleJxBrowserInstalled(mockApp, mockInspectorService, mockToolWindow);

    PowerMockito.mockStatic(DevToolsManager.class);
    when(DevToolsManager.getInstance(mockProject)).thenReturn(mockDevToolsManager);
    when(mockDevToolsManager.hasInstalledDevTools()).thenReturn(false);
    final CompletableFuture<Boolean> result = new CompletableFuture<>();
    result.complete(true);
    when(mockDevToolsManager.installDevTools()).thenReturn(result);

    when(mockApp.getConnector()).thenReturn(mockObservatoryConnector);
    when(mockObservatoryConnector.getBrowserUrl()).thenReturn(testUrl);
    when(mockToolWindow.getContentManager()).thenReturn(null);
    when(mockApp.device()).thenReturn(null);
    when(mockApp.getProject()).thenReturn(mockProject);
    when(mockProject.getName()).thenReturn(projectName);

    partialMockFlutterView.handleJxBrowserInstalled(mockApp, mockInspectorService, mockToolWindow);
    verify(partialMockFlutterView, times(1)).presentLabel(mockToolWindow, INSTALLING_DEVTOOLS_LABEL);
    verify(mockDevToolsManager, times(1)).openBrowserIntoPanel(testUrl, null, projectName, "inspector");
  }

  @Test
  public void testHandleJxBrowserInstalledWithDevtoolsFailed() {
    final String testUrl = "http://www.testUrl.com";
    final String projectName = "Test Project Name";

    final FlutterView partialMockFlutterView = mock(FlutterView.class);
    doCallRealMethod().when(partialMockFlutterView).handleJxBrowserInstalled(mockApp, mockInspectorService, mockToolWindow);

    PowerMockito.mockStatic(DevToolsManager.class);
    when(DevToolsManager.getInstance(mockProject)).thenReturn(mockDevToolsManager);
    when(mockDevToolsManager.hasInstalledDevTools()).thenReturn(false);
    final CompletableFuture<Boolean> result = new CompletableFuture<>();
    result.complete(false);
    when(mockDevToolsManager.installDevTools()).thenReturn(result);

    when(mockApp.getConnector()).thenReturn(mockObservatoryConnector);
    when(mockObservatoryConnector.getBrowserUrl()).thenReturn(testUrl);
    when(mockToolWindow.getContentManager()).thenReturn(null);
    when(mockApp.device()).thenReturn(null);
    when(mockApp.getProject()).thenReturn(mockProject);
    when(mockProject.getName()).thenReturn(projectName);

    partialMockFlutterView.handleJxBrowserInstalled(mockApp, mockInspectorService, mockToolWindow);
    verify(partialMockFlutterView, times(1)).presentLabel(mockToolWindow, INSTALLING_DEVTOOLS_LABEL);
    verify(partialMockFlutterView, times(1)).presentLabel(mockToolWindow, DEVTOOLS_FAILED_LABEL);
  }

  @Test
  public void testHandleJxBrowserInstallationFailed() {
    final FlutterView partialMockFlutterView = mock(FlutterView.class);
    doCallRealMethod().when(partialMockFlutterView).handleJxBrowserInstallationFailed(mockApp, mockInspectorService, mockToolWindow);
    partialMockFlutterView.handleJxBrowserInstallationFailed(mockApp, mockInspectorService, mockToolWindow);
    verify(partialMockFlutterView, times(1)).presentClickableLabel(
      eq(mockToolWindow),
      eq("JxBrowser installation failed. Click to retry."),
      any(LinkListener.class)
    );
  }

  @Test
  public void testHandleJxBrowserInstallationInProgressWithSuccessfulInstall() {
    final FlutterView partialMockFlutterView = mock(FlutterView.class);
    doCallRealMethod().when(partialMockFlutterView).handleJxBrowserInstallationInProgress(mockApp, mockInspectorService, mockToolWindow);

    PowerMockito.mockStatic(DevToolsManager.class);
    when(DevToolsManager.getInstance(null)).thenReturn(mockDevToolsManager);
    when(mockDevToolsManager.installDevTools()).thenReturn(null);

    PowerMockito.mockStatic(JxBrowserManager.class);
    when(JxBrowserManager.getInstance()).thenReturn(mockJxBrowserManager);
    when(mockJxBrowserManager.getStatus()).thenReturn(JxBrowserStatus.INSTALLED);

    partialMockFlutterView.handleJxBrowserInstallationInProgress(mockApp, mockInspectorService, mockToolWindow);
    verify(partialMockFlutterView, times(1)).presentLabel(mockToolWindow, INSTALLATION_IN_PROGRESS_LABEL);
    verify(partialMockFlutterView, times(1)).handleJxBrowserInstalled(mockApp, mockInspectorService, mockToolWindow);
  }

  @Test
  public void testHandleJxBrowserInstallationInProgressWaiting() {
    final FlutterView partialMockFlutterView = mock(FlutterView.class);
    doCallRealMethod().when(partialMockFlutterView).handleJxBrowserInstallationInProgress(mockApp, mockInspectorService, mockToolWindow);

    PowerMockito.mockStatic(DevToolsManager.class);
    when(DevToolsManager.getInstance(null)).thenReturn(mockDevToolsManager);
    when(mockDevToolsManager.installDevTools()).thenReturn(null);

    PowerMockito.mockStatic(JxBrowserManager.class);
    when(JxBrowserManager.getInstance()).thenReturn(mockJxBrowserManager);
    when(mockJxBrowserManager.getStatus()).thenReturn(JxBrowserStatus.INSTALLATION_IN_PROGRESS);

    partialMockFlutterView.handleJxBrowserInstallationInProgress(mockApp, mockInspectorService, mockToolWindow);
    verify(partialMockFlutterView, times(1)).presentLabel(mockToolWindow, INSTALLATION_IN_PROGRESS_LABEL);
    verify(partialMockFlutterView, times(1)).startJxBrowserInstallationWaitingThread(mockApp, mockInspectorService, mockToolWindow);
  }

  @Test
  public void testWaitForJxBrowserInstallationWithFailure() throws TimeoutException {
    final FlutterView partialMockFlutterView = mock(FlutterView.class);
    doCallRealMethod().when(partialMockFlutterView).waitForJxBrowserInstallation(mockApp, mockInspectorService, mockToolWindow);

    setUpInstallationInProgress();
    when(mockJxBrowserManager.waitForInstallation(INSTALLATION_WAIT_LIMIT_SECONDS)).thenReturn(JxBrowserStatus.INSTALLATION_FAILED);

    partialMockFlutterView.waitForJxBrowserInstallation(mockApp, mockInspectorService, mockToolWindow);
    verify(partialMockFlutterView, times(1)).handleJxBrowserInstallationFailed(mockApp, mockInspectorService, mockToolWindow);
  }

  @Test
  public void testWaitForJxBrowserInstallationWithSuccess() throws TimeoutException {
    final FlutterView partialMockFlutterView = mock(FlutterView.class);
    doCallRealMethod().when(partialMockFlutterView).waitForJxBrowserInstallation(mockApp, mockInspectorService, mockToolWindow);

    setUpInstallationInProgress();
    when(mockJxBrowserManager.waitForInstallation(INSTALLATION_WAIT_LIMIT_SECONDS)).thenReturn(JxBrowserStatus.INSTALLED);

    partialMockFlutterView.waitForJxBrowserInstallation(mockApp, mockInspectorService, mockToolWindow);
    verify(partialMockFlutterView, times(1)).handleJxBrowserInstalled(mockApp, mockInspectorService, mockToolWindow);
  }

  @Test
  public void testWaitForJxBrowserInstallationWithTimeout() throws TimeoutException {
    final FlutterView partialMockFlutterView = mock(FlutterView.class);
    doCallRealMethod().when(partialMockFlutterView).waitForJxBrowserInstallation(mockApp, mockInspectorService, mockToolWindow);

    setUpInstallationInProgress();
    when(mockJxBrowserManager.waitForInstallation(INSTALLATION_WAIT_LIMIT_SECONDS)).thenThrow(new TimeoutException());

    partialMockFlutterView.waitForJxBrowserInstallation(mockApp, mockInspectorService, mockToolWindow);
    verify(partialMockFlutterView, times(1)).presentLabel(mockToolWindow, INSTALLATION_TIMED_OUT_LABEL);
  }

  private void setUpInstallationInProgress() {
    PowerMockito.mockStatic(DevToolsManager.class);
    when(DevToolsManager.getInstance(null)).thenReturn(mockDevToolsManager);
    when(mockDevToolsManager.installDevTools()).thenReturn(null);

    PowerMockito.mockStatic(JxBrowserManager.class);
    when(mockJxBrowserManager.getStatus()).thenReturn(JxBrowserStatus.INSTALLATION_IN_PROGRESS);
    when(JxBrowserManager.getInstance()).thenReturn(mockJxBrowserManager);
  }
}
