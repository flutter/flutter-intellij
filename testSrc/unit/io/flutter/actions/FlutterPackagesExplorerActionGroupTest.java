/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * Verifies {@link FlutterPackagesExplorerActionGroup} and its registration in {@code ProjectViewPopupMenu}.
 */
public class FlutterPackagesExplorerActionGroupTest extends BasePlatformTestCase {

  private static final String GROUP_ID = "FlutterPackagesExplorerActionGroup";
  private static final String ANCHOR_TARGET_ID = "ProjectViewPopupMenuModifyGroup";

  public void testProjectViewPopupMenuAnchoring() {
    final ActionManager actionManager = ActionManager.getInstance();
    final DefaultActionGroup popupGroup = assertInstanceOf(
      actionManager.getAction(IdeActions.GROUP_PROJECT_VIEW_POPUP),
      DefaultActionGroup.class
    );
    final List<String> childIds = Arrays.stream(popupGroup.getChildren(actionManager))
      .map(actionManager::getId)
      .toList();

    assertInstanceOf(actionManager.getAction(GROUP_ID), FlutterPackagesExplorerActionGroup.class);
    assertNotNull(
      "Anchor target '" + ANCHOR_TARGET_ID + "' should exist in ActionManager (children: " + childIds + ")",
      actionManager.getAction(ANCHOR_TARGET_ID)
    );

    final int cutCopyPasteIndex = childIds.indexOf(IdeActions.GROUP_CUT_COPY_PASTE);
    final int flutterIndex = childIds.indexOf(GROUP_ID);
    final int anchorIndex = childIds.indexOf(ANCHOR_TARGET_ID);
    assertTrue("CutCopyPasteGroup should exist in ProjectViewPopupMenu (children: " + childIds + ")", cutCopyPasteIndex >= 0);
    assertTrue(GROUP_ID + " should follow CutCopyPasteGroup in ProjectViewPopupMenu (children: " + childIds + ")",
               flutterIndex > cutCopyPasteIndex);
    assertTrue(GROUP_ID + " should precede " + ANCHOR_TARGET_ID + " in ProjectViewPopupMenu (children: " + childIds + ")",
               flutterIndex < anchorIndex);
  }

  public void testUpdateEnablesAndShowsForFlutterPubspec() {
    final VirtualFile flutterPubspec = myFixture.addFileToProject(
      "pubspec.yaml",
      "name: my_app\ndependencies:\n  flutter:\n    sdk: flutter\n"
    ).getVirtualFile();

    assertGroupEnabledAndVisible(flutterPubspec, true);
  }

  public void testUpdateDisablesAndHidesForNonFlutterTargets() {
    myFixture.addFileToProject(
      "pubspec.yaml",
      "name: my_app\ndependencies:\n  flutter:\n    sdk: flutter\n"
    );
    final VirtualFile pureDartPubspec = myFixture.addFileToProject(
      "packages/pure_dart/pubspec.yaml",
      "name: pure_dart\ndependencies:\n  path: ^1.9.0\n"
    ).getVirtualFile();
    final VirtualFile siblingReadme = myFixture.addFileToProject("README.md", "").getVirtualFile();
    final VirtualFile nestedSource = myFixture.addFileToProject("lib/main.dart", "void main() {}\n").getVirtualFile();

    assertGroupEnabledAndVisible(pureDartPubspec, false);
    assertGroupEnabledAndVisible(siblingReadme, false);
    assertGroupEnabledAndVisible(nestedSource, false);
    assertGroupEnabledAndVisible(nestedSource.getParent(), false);
    assertGroupEnabledAndVisible(null, false);
  }

  private void assertGroupEnabledAndVisible(@Nullable VirtualFile file, boolean expected) {
    final FlutterPackagesExplorerActionGroup group = assertInstanceOf(
      ActionManager.getInstance().getAction(GROUP_ID),
      FlutterPackagesExplorerActionGroup.class
    );
    final DataContext context = file == null
      ? DataContext.EMPTY_CONTEXT
      : SimpleDataContext.builder().add(CommonDataKeys.VIRTUAL_FILE, file).build();
    final Presentation presentation = new Presentation();
    presentation.setEnabledAndVisible(!expected);
    final AnActionEvent event = AnActionEvent.createEvent(
      context, presentation, ActionPlaces.PROJECT_VIEW_POPUP, ActionUiKind.POPUP, null
    );

    group.update(event);

    assertEquals(expected, presentation.isEnabled());
    assertEquals(expected, presentation.isVisible());
  }
}
