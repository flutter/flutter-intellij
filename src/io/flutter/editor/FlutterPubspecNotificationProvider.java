/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.editor;

import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import icons.FlutterIcons;
import io.flutter.FlutterConstants;
import io.flutter.sdk.FlutterSdk;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class FlutterPubspecNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("flutter.pubspec");

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Nullable
  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor editor) {
    if (!file.isInLocalFileSystem()) {
      return null;
    }

    if (!FlutterConstants.PUBSPEC_YAML.equalsIgnoreCase(file.getName())) {
      return null;
    }

    // Check for a flutter sdk.
    final Project project = ProjectLocator.getInstance().guessProjectForFile(file);
    if (project == null) {
      return null;
    }

    final Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (module == null || !FlutterModuleUtils.isFlutterModule(module)) {
      return null;
    }

    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null) {
      return null;
    }

    return new FlutterPubspecActionsPanel(file);
  }

  static class FlutterPubspecActionsPanel extends EditorNotificationPanel {
    @NotNull final VirtualFile myFile;

    FlutterPubspecActionsPanel(@NotNull VirtualFile file) {
      myFile = file;

      icon(FlutterIcons.Flutter);
      text("Flutter commands");

      createActionLabel("Packages get", "flutter.packages.get");
      createActionLabel("Packages upgrade", "flutter.packages.upgrade");
      myLinksPanel.add(new JSeparator(SwingConstants.VERTICAL));
      createActionLabel("Flutter upgrade", "flutter.upgrade");
      myLinksPanel.add(new JSeparator(SwingConstants.VERTICAL));
      createActionLabel("Flutter doctor", "flutter.doctor");

      // TODO: Add for 2017.1.
      //background(EditorColors.GUTTER_BACKGROUND);
    }

    // TODO: Remove for 2017.1.
    @Override
    public Color getBackground() {
      final Color color = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.GUTTER_BACKGROUND);
      return color != null ? color : super.getBackground();
    }
  }
}
