/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspections;

import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class FlutterYamlNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("flutter.yaml");
  public static final String FLUTTER_YAML_NAME = "flutter.yaml";

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

    if (!FLUTTER_YAML_NAME.equalsIgnoreCase(file.getName())) {
      return null;
    }

    // Check for a flutter sdk.
    final Project project = ProjectLocator.getInstance().guessProjectForFile(file);
    if (project == null) {
      return null;
    }

    if (!FlutterSdkUtil.hasFlutterModule(project)) {
      return null;
    }

    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null) {
      return null;
    }

    return new FlutterYamlActionsPanel(file);
  }
}

class FlutterYamlActionsPanel extends EditorNotificationPanel {
  @NotNull final VirtualFile myFile;

  FlutterYamlActionsPanel(@NotNull VirtualFile file) {
    myFile = file;

    myLinksPanel.add(new JLabel("Flutter actions:"));
    createActionLabel("Flutter upgrade", "flutter.upgrade");
    myLinksPanel.add(new JLabel("        "));
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
