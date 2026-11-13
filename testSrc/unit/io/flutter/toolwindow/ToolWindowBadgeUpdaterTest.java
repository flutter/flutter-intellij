/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.LayeredIcon;
import io.flutter.run.common.RunMode;
import io.flutter.run.daemon.FlutterApp;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import javax.swing.Icon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ToolWindowBadgeUpdater}.
 */
public class ToolWindowBadgeUpdaterTest {
  private Project project;
  private ToolWindowManager toolWindowManager;
  private ToolWindow runToolWindow;
  private ToolWindow debugToolWindow;
  private FlutterApp app;

  @Before
  public void setUp() {
    project = mock(Project.class);
    toolWindowManager = mock(ToolWindowManager.class);
    runToolWindow = mock(ToolWindow.class);
    debugToolWindow = mock(ToolWindow.class);
    app = mock(FlutterApp.class);

    when(project.getService(ToolWindowManager.class)).thenReturn(toolWindowManager);
    when(toolWindowManager.getToolWindow(ToolWindowId.RUN)).thenReturn(runToolWindow);
    when(toolWindowManager.getToolWindow(ToolWindowId.DEBUG)).thenReturn(debugToolWindow);
    doAnswer(invocation -> {
      final Runnable runnable = invocation.getArgument(0);
      runnable.run();
      return null;
    }).when(toolWindowManager).invokeLater(any(Runnable.class));
  }

  @Test
  public void updateBadgedIcon_runModeBadgesRunToolWindow() {
    when(app.getMode()).thenReturn(RunMode.RUN);

    ToolWindowBadgeUpdater.updateBadgedIcon(app, project);

    final ArgumentCaptor<Icon> iconCaptor = ArgumentCaptor.forClass(Icon.class);
    verify(runToolWindow).setIcon(iconCaptor.capture());
    verify(debugToolWindow, never()).setIcon(any());

    final Icon icon = iconCaptor.getValue();
    assertTrue(icon instanceof LayeredIcon);
    final LayeredIcon layeredIcon = (LayeredIcon)icon;
    assertEquals(2, layeredIcon.getAllLayers().length);
    assertEquals(AllIcons.Toolwindows.ToolWindowRun, layeredIcon.getIcon(0));
    assertEquals(AllIcons.Nodes.RunnableMark, layeredIcon.getIcon(1));
  }

  @Test
  public void updateBadgedIcon_debugModeBadgesDebugToolWindow() {
    when(app.getMode()).thenReturn(RunMode.DEBUG);

    ToolWindowBadgeUpdater.updateBadgedIcon(app, project);

    final ArgumentCaptor<Icon> iconCaptor = ArgumentCaptor.forClass(Icon.class);
    verify(debugToolWindow).setIcon(iconCaptor.capture());
    verify(runToolWindow, never()).setIcon(any());

    final Icon icon = iconCaptor.getValue();
    assertTrue(icon instanceof LayeredIcon);
    final LayeredIcon layeredIcon = (LayeredIcon)icon;
    assertEquals(2, layeredIcon.getAllLayers().length);
    assertEquals(AllIcons.Toolwindows.ToolWindowDebugger, layeredIcon.getIcon(0));
    assertEquals(AllIcons.Nodes.RunnableMark, layeredIcon.getIcon(1));
  }

  @Test
  public void updateBadgedIcon_profileModeDoesNotBadgeToolWindows() {
    when(app.getMode()).thenReturn(RunMode.PROFILE);

    ToolWindowBadgeUpdater.updateBadgedIcon(app, project);

    verify(runToolWindow, never()).setIcon(any());
    verify(debugToolWindow, never()).setIcon(any());
  }

  @Test
  public void updateBadgedIcon_nullRunToolWindowDoesNotThrow() {
    when(app.getMode()).thenReturn(RunMode.RUN);
    when(toolWindowManager.getToolWindow(ToolWindowId.RUN)).thenReturn(null);

    ToolWindowBadgeUpdater.updateBadgedIcon(app, project);

    verify(toolWindowManager, never()).invokeLater(any());
  }

  @Test
  public void updateBadgedIcon_nullDebugToolWindowDoesNotThrow() {
    when(app.getMode()).thenReturn(RunMode.DEBUG);
    when(toolWindowManager.getToolWindow(ToolWindowId.DEBUG)).thenReturn(null);

    ToolWindowBadgeUpdater.updateBadgedIcon(app, project);

    verify(toolWindowManager).invokeLater(any());
    verify(runToolWindow, never()).setIcon(any());
  }
}
