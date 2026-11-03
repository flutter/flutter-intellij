/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import io.flutter.testing.CodeInsightProjectFixture;
import io.flutter.testing.Testing;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NativeEditorNotificationProvider}.
 */
public class NativeEditorNotificationProviderTest {

  private static final String TEST_ACTION_ID = "flutter.test.native.editor.action";

  @Rule
  public final CodeInsightProjectFixture fixture = Testing.makeCodeInsightModule();

  @Test
  public void panelUpdatesAndPerformsActionWithoutMutatingTemplatePresentation() throws Exception {
    final AtomicBoolean visibleInUpdate = new AtomicBoolean(false);
    final AtomicInteger updateCount = new AtomicInteger();
    final AtomicInteger performCount = new AtomicInteger();
    final AtomicReference<Project> receivedProject = new AtomicReference<>();
    final AtomicReference<VirtualFile> receivedFile = new AtomicReference<>();

    final AnAction testAction = new DumbAwareAction("Template Text", "Template Description", null) {
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        updateCount.incrementAndGet();
        e.getPresentation().setVisible(visibleInUpdate.get());
        e.getPresentation().setText("Updated Label");
        e.getPresentation().setDescription("Updated Description");
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        performCount.incrementAndGet();
        receivedProject.set(e.getProject());
        receivedFile.set(e.getData(CommonDataKeys.VIRTUAL_FILE));
        e.getPresentation().setText("Mutated During Perform");
      }
    };

    final ActionManager actionManager = ActionManager.getInstance();
    actionManager.registerAction(TEST_ACTION_ID, testAction);
    try {
      final FileEditor fileEditor = mock(FileEditor.class);
      final VirtualFile file = new LightVirtualFile("AppDelegate.swift", "");
      final VirtualFile root = new LightVirtualFile("ios", "");
      when(fileEditor.getFile()).thenReturn(file);

      Testing.runOnDispatchThread(() -> {
        final NativeEditorNotificationProvider provider = new NativeEditorNotificationProvider(fixture.getProject());

        // 1. Action hides presentation in update(): panel.isVisible is false, template presentation stays untouched.
        final NativeEditorNotificationProvider.NativeEditorActionsPanel hiddenPanel =
          provider.new NativeEditorActionsPanel(fileEditor, root, TEST_ACTION_ID);
        assertEquals(1, updateCount.get());
        assertFalse(hiddenPanel.isVisible);
        assertTrue(testAction.getTemplatePresentation().isVisible());
        assertEquals("Template Text", testAction.getTemplatePresentation().getText());
        assertEquals("Template Description", testAction.getTemplatePresentation().getDescription());

        // 2. Action enables visibility in update(): panel.isVisible is true and performAction() dispatches via ActionUtil.
        visibleInUpdate.set(true);
        final NativeEditorNotificationProvider.NativeEditorActionsPanel visiblePanel =
          provider.new NativeEditorActionsPanel(fileEditor, root, TEST_ACTION_ID);
        assertEquals(2, updateCount.get());
        assertTrue(visiblePanel.isVisible);
        assertEquals("Template Text", testAction.getTemplatePresentation().getText());

        visiblePanel.performAction();
        assertEquals(1, performCount.get());
        assertSame(fixture.getProject(), receivedProject.get());
        assertSame(file, receivedFile.get());
        assertEquals("Template Text", testAction.getTemplatePresentation().getText());
      });
    }
    finally {
      actionManager.unregisterAction(TEST_ACTION_ID);
    }
  }
}
