/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import icons.FlutterIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO(devoncarew): Are we showing the 'Open in Xcode' editor action on non-mac platforms?

public class NativeEditorNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("flutter.native.editor.notification");

  private final Project myProject;

  public NativeEditorNotificationProvider(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    if (!file.isInLocalFileSystem()) {
      return null;
    }
    return createPanelForFile(file, findRootDir(file, myProject.getBaseDir()));
  }

  private EditorNotificationPanel createPanelForFile(VirtualFile file, VirtualFile root) {
    if (root == null) {
      return null;
    }
    return createPanelForAction(file, root, getActionName(root));
  }

  private EditorNotificationPanel createPanelForAction(VirtualFile file, VirtualFile root, String actionName) {
    if (actionName == null) {
      return null;
    }
    NativeEditorActionsPanel panel = new NativeEditorActionsPanel(file, root, actionName);
    return panel.isValidForFile() ? panel : null;
  }

  private static String getActionName(VirtualFile root) {
    if (root == null) {
      return null;
    }

    //noinspection IfCanBeSwitch
    if (root.getName().equals("android")) {
      return "flutter.androidstudio.open";
    }
    else if (root.getName().equals("ios")) {
      return "flutter.xcode.open";
    }
    else {
      return null;
    }
  }

  private static VirtualFile findRootDir(@NotNull VirtualFile file, VirtualFile projectDir) {
    // Return the top-most parent of file that is a child of the project directory.
    VirtualFile parent = file.getParent();
    if (projectDir.equals(parent)) {
      return null;
    }
    VirtualFile root = parent;
    while (parent != null) {
      parent = parent.getParent();
      if (projectDir.equals(parent)) {
        return root;
      }
      root = parent;
    }
    return null;
  }

  class NativeEditorActionsPanel extends EditorNotificationPanel {
    final VirtualFile myFile;
    final VirtualFile myRoot;
    final AnAction myAction;
    final boolean isVisible;

    NativeEditorActionsPanel(VirtualFile file, VirtualFile root, String actionName) {
      super(EditorColors.GUTTER_BACKGROUND);
      myFile = file;
      myRoot = root;
      myAction = ActionManager.getInstance().getAction(actionName);

      icon(FlutterIcons.Flutter);
      text("Flutter commands");

      // Ensure this project is a Flutter project by updating the menu action. It will only be visible for Flutter projects.
      myAction.update(AnActionEvent.createFromDataContext(ActionPlaces.EDITOR_TOOLBAR, myAction.getTemplatePresentation(), makeContext()));
      isVisible = myAction.getTemplatePresentation().isVisible();
      createActionLabel(myAction.getTemplatePresentation().getText(), this::performAction)
        .setToolTipText(myAction.getTemplatePresentation().getDescription());
    }

    private boolean isValidForFile() {
      if (isVisible) {
        // The menu items are visible for certain elements outside the module directories.
        return FileUtil.isAncestor(myRoot.getPath(), myFile.getPath(), true);
      }
      return false;
    }

    private void performAction() {
      // Open Xcode or Android Studio. If already running AS then just open a new window.
      myAction.actionPerformed(
        AnActionEvent.createFromDataContext(ActionPlaces.EDITOR_TOOLBAR, myAction.getTemplatePresentation(), makeContext()));
    }

    private DataContext makeContext() {
      return dataId -> {
        if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
          return myFile;
        }
        if (CommonDataKeys.PROJECT.is(dataId)) {
          return myProject;
        }
        return null;
      };
    }
  }
}
