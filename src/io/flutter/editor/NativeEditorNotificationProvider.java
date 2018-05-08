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
import com.intellij.ui.HyperlinkLabel;
import icons.FlutterIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    final VirtualFile root = findRootDir(file);
    if (root == null) {
      return null;
    }

    final String actionName;
    if (root.getName().equals("android")) {
      actionName = "flutter.androidstudio.open";
    }
    else if (root.getName().equals("ios")) {
      actionName = "flutter.xcode.open";
    }
    else {
      return null;
    }

    NativeEditorActionsPanel panel = new NativeEditorActionsPanel(file, root, actionName);
    return panel.isValidForFile() ? panel : null;
  }

  private VirtualFile findRootDir(@NotNull VirtualFile file) {
    // Return the top-most parent of file that is a child of the project directory.
    final VirtualFile projectDir = myProject.getBaseDir();
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

    NativeEditorActionsPanel(VirtualFile file, VirtualFile root, String actionName) {
      super(EditorColors.GUTTER_BACKGROUND);
      myFile = file;
      myRoot = root;
      myAction = ActionManager.getInstance().getAction(actionName);

      icon(FlutterIcons.Flutter);
      text("Flutter commands");

      final Presentation present = myAction.getTemplatePresentation();
      final HyperlinkLabel label = createActionLabel(present.getText(), this::performAction);
      label.setToolTipText(present.getDescription());
    }

    private boolean isValidForFile() {
      final DataContext context = makeContext();
      final AnActionEvent event = AnActionEvent.createFromDataContext(ActionPlaces.EDITOR_TOOLBAR, null, context);
      // Ensure this project is a Flutter project by updating the menu action. It will only be visible for Flutter projects.
      myAction.update(event);
      if (event.getPresentation().isVisible()) {
        // The menu items are visible for certain elements outside the module directories.
        return FileUtil.isAncestor(myRoot.getPath(), myFile.getPath(), true);
      }
      return false;
    }

    private void performAction() {
      final Presentation present = myAction.getTemplatePresentation();
      final DataContext context = makeContext();
      final AnActionEvent event = AnActionEvent.createFromDataContext(ActionPlaces.EDITOR_TOOLBAR, present, context);
      // Open Xcode or Android Studio. If already running AS then just open a new window.
      myAction.actionPerformed(event);
    }

    private DataContext makeContext() {
      return new DataContext() {
        @Override
        @Nullable
        public Object getData(@NonNls String dataId) {
          if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
            return myFile;
          }
          if (CommonDataKeys.PROJECT.is(dataId)) {
            return myProject;
          }
          return null;
        }
      };
    }
  }
}
