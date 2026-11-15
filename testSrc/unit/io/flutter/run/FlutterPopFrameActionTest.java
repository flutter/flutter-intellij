/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.xdebugger.XDebugSession;
import io.flutter.testing.CodeInsightProjectFixture;
import io.flutter.testing.Testing;
import io.flutter.vmService.frame.DartVmServiceStackFrame;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FlutterPopFrameAction}.
 */
public class FlutterPopFrameActionTest {

  @Rule
  public final CodeInsightProjectFixture fixture = Testing.makeCodeInsightModule();

  @Test
  public void updateTogglesEnabledForToolbarMainMenuAndSearchPopup() {
    final FlutterPopFrameAction action = new FlutterPopFrameAction();
    final DataContext disabledContext = createSessionContext(false);
    final DataContext enabledContext = createSessionContext(true);

    // Toolbar UI kind: stays visible, toggles enabled.
    final Presentation toolbarDisabled = new Presentation();
    action.update(AnActionEvent.createEvent(disabledContext, toolbarDisabled, ActionPlaces.TOOLBAR, ActionUiKind.TOOLBAR, null));
    assertTrue(toolbarDisabled.isVisible());
    assertFalse(toolbarDisabled.isEnabled());

    final Presentation toolbarEnabled = new Presentation();
    action.update(AnActionEvent.createEvent(enabledContext, toolbarEnabled, ActionPlaces.TOOLBAR, ActionUiKind.TOOLBAR, null));
    assertTrue(toolbarEnabled.isVisible());
    assertTrue(toolbarEnabled.isEnabled());

    // Main menu UI kind: stays visible, toggles enabled.
    final Presentation mainMenuDisabled = new Presentation();
    action.update(AnActionEvent.createEvent(disabledContext, mainMenuDisabled, ActionPlaces.MAIN_MENU, ActionUiKind.MAIN_MENU, null));
    assertTrue(mainMenuDisabled.isVisible());
    assertFalse(mainMenuDisabled.isEnabled());

    // Search popup (Go To Action / Search Everywhere) UI kind: stays visible, toggles enabled.
    final Presentation searchDisabled = new Presentation();
    action.update(AnActionEvent.createEvent(disabledContext, searchDisabled, ActionPlaces.ACTION_SEARCH, ActionUiKind.SEARCH_POPUP, null));
    assertTrue(searchDisabled.isVisible());
    assertFalse(searchDisabled.isEnabled());

    // Explicit DebuggerToolbar place with NONE UI kind: stays visible, toggles enabled.
    final Presentation debuggerToolbarDisabled = new Presentation();
    action.update(
      AnActionEvent.createEvent(disabledContext, debuggerToolbarDisabled, ActionPlaces.DEBUGGER_TOOLBAR, ActionUiKind.NONE, null));
    assertTrue(debuggerToolbarDisabled.isVisible());
    assertFalse(debuggerToolbarDisabled.isEnabled());
  }

  @Test
  public void updateTogglesVisibleForContextMenus() {
    final FlutterPopFrameAction action = new FlutterPopFrameAction();

    final Presentation popupDisabled = new Presentation();
    action.update(
      AnActionEvent.createEvent(createSessionContext(false), popupDisabled, ActionPlaces.EDITOR_POPUP, ActionUiKind.POPUP, null));
    assertFalse(popupDisabled.isVisible());

    final Presentation popupEnabled = new Presentation();
    action.update(
      AnActionEvent.createEvent(createSessionContext(true), popupEnabled, ActionPlaces.EDITOR_POPUP, ActionUiKind.POPUP, null));
    assertTrue(popupEnabled.isVisible());
  }

  @Test
  public void actionPerformedDropsCurrentDartStackFrame() {
    final FlutterPopFrameAction action = new FlutterPopFrameAction();
    final DartVmServiceStackFrame frame = mock(DartVmServiceStackFrame.class);
    final XDebugSession session = mock(XDebugSession.class);
    when(session.getCurrentStackFrame()).thenReturn(frame);

    final DataContext context = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, fixture.getProject())
      .add(XDebugSession.DATA_KEY, session)
      .build();
    final AnActionEvent event =
      AnActionEvent.createEvent(context, new Presentation(), ActionPlaces.DEBUGGER_TOOLBAR, ActionUiKind.TOOLBAR, null);

    action.actionPerformed(event);
    verify(frame).dropFrame();
  }

  private DataContext createSessionContext(@Nullable Boolean canDrop) {
    final SimpleDataContext.Builder builder = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, fixture.getProject());
    if (canDrop != null) {
      final DartVmServiceStackFrame frame = mock(DartVmServiceStackFrame.class);
      when(frame.canDrop()).thenReturn(canDrop);
      final XDebugSession session = mock(XDebugSession.class);
      when(session.getCurrentStackFrame()).thenReturn(frame);
      builder.add(XDebugSession.DATA_KEY, session);
    }
    return builder.build();
  }
}
