/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

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

    final AnAction popupMenuAction = actionManager.getAction(IdeActions.GROUP_PROJECT_VIEW_POPUP);
    assertNotNull("ProjectViewPopupMenu should be registered", popupMenuAction);
    assertTrue("ProjectViewPopupMenu should be a DefaultActionGroup", popupMenuAction instanceof DefaultActionGroup);

    final DefaultActionGroup popupGroup = (DefaultActionGroup)popupMenuAction;
    final List<AnAction> children = Arrays.asList(popupGroup.getChildren(actionManager));
    final List<String> childIds = children.stream().map(actionManager::getId).toList();

    final AnAction flutterGroup = actionManager.getAction(GROUP_ID);
    assertNotNull(GROUP_ID + " should be registered", flutterGroup);

    final AnAction anchorTarget = actionManager.getAction(ANCHOR_TARGET_ID);
    assertNotNull("Anchor target '" + ANCHOR_TARGET_ID + "' should exist in ActionManager (children: " + childIds + ")", anchorTarget);

    final int flutterIndex = childIds.indexOf(GROUP_ID);
    final int anchorIndex = childIds.indexOf(ANCHOR_TARGET_ID);

    assertTrue(GROUP_ID + " should be a child of ProjectViewPopupMenu (children: " + childIds + ")", flutterIndex >= 0);
    assertTrue(ANCHOR_TARGET_ID + " should be a child of ProjectViewPopupMenu (children: " + childIds + ")", anchorIndex >= 0);
    assertEquals(
      GROUP_ID + " should be anchored immediately before " + ANCHOR_TARGET_ID + " (children: " + childIds + ")",
      anchorIndex - 1,
      flutterIndex
    );
    assertTrue(
      GROUP_ID + " should not fall back to the last entry in ProjectViewPopupMenu (children: " + childIds + ")",
      flutterIndex < children.size() - 1
    );
  }

  public void testUpdateEnablesAndShowsForFlutterPubspec() {
    final VirtualFile vPubspec = myFixture.addFileToProject(
      "pubspec.yaml",
      "name: my_app\ndependencies:\n  flutter:\n    sdk: flutter\n"
    ).getVirtualFile();
    final VirtualFile vOther = myFixture.addFileToProject(
      "README.md",
      ""
    ).getVirtualFile();

    final FlutterPackagesExplorerActionGroup group = new FlutterPackagesExplorerActionGroup();

    // 1. Flutter pubspec.yaml -> enabled & visible
    final DataContext pubspecContext = SimpleDataContext.getSimpleContext(CommonDataKeys.VIRTUAL_FILE, vPubspec);
    final Presentation pubspecPresentation = new Presentation();
    final AnActionEvent pubspecEvent = AnActionEvent.createEvent(
      pubspecContext, pubspecPresentation, ActionPlaces.PROJECT_VIEW_POPUP, ActionUiKind.POPUP, null
    );
    group.update(pubspecEvent);
    assertTrue(pubspecPresentation.isEnabled());
    assertTrue(pubspecPresentation.isVisible());

    // 2. Non-pubspec file -> disabled & hidden
    final DataContext otherContext = SimpleDataContext.getSimpleContext(CommonDataKeys.VIRTUAL_FILE, vOther);
    final Presentation otherPresentation = new Presentation();
    final AnActionEvent otherEvent = AnActionEvent.createEvent(
      otherContext, otherPresentation, ActionPlaces.PROJECT_VIEW_POPUP, ActionUiKind.POPUP, null
    );
    group.update(otherEvent);
    assertFalse(otherPresentation.isEnabled());
    assertFalse(otherPresentation.isVisible());

    // 3. Empty context (no file) -> disabled & hidden
    final Presentation emptyPresentation = new Presentation();
    final AnActionEvent emptyEvent = AnActionEvent.createEvent(
      DataContext.EMPTY_CONTEXT, emptyPresentation, ActionPlaces.PROJECT_VIEW_POPUP, ActionUiKind.POPUP, null
    );
    group.update(emptyEvent);
    assertFalse(emptyPresentation.isEnabled());
    assertFalse(emptyPresentation.isVisible());
  }
}
