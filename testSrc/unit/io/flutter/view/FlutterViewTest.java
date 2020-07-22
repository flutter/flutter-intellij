/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.view;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import io.flutter.ObservatoryConnector;
import io.flutter.devtools.DevToolsManager;
import io.flutter.inspector.InspectorService;
import io.flutter.run.daemon.FlutterApp;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(DevToolsManager.class)
public class FlutterViewTest {
  @Mock Project mockProject;
  @Mock FlutterApp mockApp;
  @Mock InspectorService mockInspectorService;
  @Mock ToolWindow mockToolWindow;
  @Mock DevToolsManager mockDevToolsManager;
  @Mock ObservatoryConnector mockObservatoryConnector;

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
}
