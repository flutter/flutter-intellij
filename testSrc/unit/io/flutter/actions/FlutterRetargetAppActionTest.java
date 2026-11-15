/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import io.flutter.testing.CodeInsightProjectFixture;
import io.flutter.testing.Testing;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link FlutterRetargetAppAction}.
 */
public class FlutterRetargetAppActionTest {

  private static final String TARGET_ACTION_ID = "flutter.test.retarget.target";

  @Rule
  public final CodeInsightProjectFixture fixture = Testing.makeCodeInsightModule();

  private static final class TestRetargetAction extends FlutterRetargetAppAction {
    TestRetargetAction(@NotNull String actionId, @NotNull String... places) {
      super(actionId, "Retarget Text", "Retarget Description", places);
    }
  }

  @Test
  public void actionPerformedDelegatesThroughActionUtil() throws Exception {
    final Project project = fixture.getProject();
    final AtomicInteger performCount = new AtomicInteger();
    final AnAction target = new DumbAwareAction("Target Action") {
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        performCount.incrementAndGet();
      }
    };

    ProjectActions.registerAction(project, TARGET_ACTION_ID, target);
    try {
      final TestRetargetAction retarget = new TestRetargetAction(TARGET_ACTION_ID, ActionPlaces.MAIN_MENU);
      final DataContext context = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).build();
      final AnActionEvent event =
        AnActionEvent.createEvent(context, new Presentation(), ActionPlaces.MAIN_MENU, ActionUiKind.MAIN_MENU, null);

      Testing.runOnDispatchThread(() -> retarget.actionPerformed(event));
      assertEquals(1, performCount.get());
    }
    finally {
      ProjectActions.unregisterAction(project, TARGET_ACTION_ID);
    }
  }

  @Test
  public void updateDelegatesThroughActionUtilWhenFlutterModuleAndPlaceMatch() {
    final Project project = fixture.getProject();
    final AtomicInteger updateCount = new AtomicInteger();
    final AnAction target = new DumbAwareAction("Hot _Reload") {
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        updateCount.incrementAndGet();
        e.getPresentation().setEnabled(true);
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
      }
    };

    ProjectActions.registerAction(project, TARGET_ACTION_ID, target);
    try (MockedStatic<FlutterModuleUtils> moduleUtils = Mockito.mockStatic(FlutterModuleUtils.class)) {
      moduleUtils.when(() -> FlutterModuleUtils.hasFlutterModule(project)).thenReturn(true);

      final TestRetargetAction retarget = new TestRetargetAction(TARGET_ACTION_ID, ActionPlaces.MAIN_MENU);
      final DataContext context = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).build();
      final Presentation presentation = new Presentation();
      final AnActionEvent matchingEvent =
        AnActionEvent.createEvent(context, presentation, ActionPlaces.MAIN_MENU, ActionUiKind.MAIN_MENU, null);

      retarget.update(matchingEvent);
      assertEquals(1, updateCount.get());
      assertTrue(presentation.isVisible());
      assertTrue(presentation.isEnabled());
      assertEquals("Hot Reload", presentation.getText());

      final Presentation wrongPlacePresentation = new Presentation();
      final AnActionEvent wrongPlaceEvent =
        AnActionEvent.createEvent(context, wrongPlacePresentation, ActionPlaces.EDITOR_POPUP, ActionUiKind.POPUP, null);
      retarget.update(wrongPlaceEvent);
      assertFalse(wrongPlacePresentation.isVisible());
      assertEquals(1, updateCount.get());
    }
    finally {
      ProjectActions.unregisterAction(project, TARGET_ACTION_ID);
    }
  }

  @Test
  public void updateHidesPresentationWhenProjectMissingOrNonFlutter() {
    final TestRetargetAction retarget = new TestRetargetAction(TARGET_ACTION_ID, ActionPlaces.MAIN_MENU);

    final Presentation noProjectPresentation = new Presentation();
    final AnActionEvent noProjectEvent =
      AnActionEvent.createEvent(DataContext.EMPTY_CONTEXT, noProjectPresentation, ActionPlaces.MAIN_MENU, ActionUiKind.MAIN_MENU, null);
    retarget.update(noProjectEvent);
    assertFalse(noProjectPresentation.isVisible());

    final DataContext projectContext = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, fixture.getProject()).build();
    final Presentation nonFlutterPresentation = new Presentation();
    final AnActionEvent nonFlutterEvent =
      AnActionEvent.createEvent(projectContext, nonFlutterPresentation, ActionPlaces.MAIN_MENU, ActionUiKind.MAIN_MENU, null);
    retarget.update(nonFlutterEvent);
    assertFalse(nonFlutterPresentation.isVisible());
  }
}
