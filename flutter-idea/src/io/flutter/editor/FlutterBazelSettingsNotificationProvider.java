/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import io.flutter.FlutterUtils;
import io.flutter.bazel.Workspace;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.utils.UIUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This notification provider checks plugin dependencies, provided from the Bazel workspace.
 */
public class FlutterBazelSettingsNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("flutter.bazel");

  public FlutterBazelSettingsNotificationProvider(@NotNull Project project) {
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file,
                                                         @NotNull FileEditor fileEditor,
                                                         @NotNull Project project) {
    // This notification panel should be ignored if this is not Dart file or if this is not a Bazel configuration.
    final WorkspaceCache workspaceCache = WorkspaceCache.getInstance(project);
    if (!FlutterUtils.isDartFile(file) || !workspaceCache.isBazel()) {
      return null;
    }

    final Workspace workspace = workspaceCache.get();
    // The workspace is not null if workspaceCache.isBazel() is true.
    assert workspace != null;

    final String requiredIJPluginID = workspace.getRequiredIJPluginID();
    final String requiredIJPluginMessage = workspace.getRequiredIJPluginMessage();

    if (StringUtil.isEmpty(requiredIJPluginID) || StringUtil.isEmpty(requiredIJPluginMessage)) {
      return null;
    }

    final IdeaPluginDescriptor pluginDescriptor = FlutterUtils.getPluginDescriptor(requiredIJPluginID);
    if (pluginDescriptor == null || !pluginDescriptor.isEnabled()) {
      return new MissingSuggestedPluginActionsPanel(requiredIJPluginMessage);
    }
    return null;
  }

  static class MissingSuggestedPluginActionsPanel extends EditorNotificationPanel {
    MissingSuggestedPluginActionsPanel(@NotNull String fixMessage) {
      super(UIUtils.getEditorNotificationBackgroundColor());

      // This action panel simply displays the message provided by the workspace.
      text(fixMessage);
    }
  }
}
