/*
 * Copyright 2024 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspections;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.pub.PubRoot;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

/**
 * This EditorNotificationProvider opens a banner at the top of analysis_options.yaml files and directs developers to reference the
 * documentation at dart.dev/tools/analysis
 */
public class AnalysisOptionsNotificationProvider implements EditorNotificationProvider {
  private static final String DOC_URL = "https://dart.dev/tools/analysis";

  @Override
  @Nullable
  public Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                       @NotNull VirtualFile file) {
    // If this is a Bazel configured Flutter project, exit immediately, neither of the notifications should be shown for this project type.
    if (FlutterModuleUtils.isFlutterBazelProject(project)) return null;

    // If the file name does not match "analysis_options.yaml"
    if (!file.getName().equals(PubRoot.ANALYSIS_OPTIONS_YAML)) return null;

    final Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (!FlutterModuleUtils.isFlutterModule(module)) return null;

    return fileEditor -> createAnalysisOptionsPanel(fileEditor, project);
  }

  private static EditorNotificationPanel createAnalysisOptionsPanel(@NotNull FileEditor fileEditor, @NotNull Project project) {
    final EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.icon(FlutterIcons.Flutter);
    panel.setText(FlutterBundle.message("flutter.analysis.options.docs"));
    // When the user dismissed this banner, it goes away, but will appear when the user opens the file again
    panel.createActionLabel(DOC_URL, () -> BrowserUtil.browse(DOC_URL));
    panel.createActionLabel("Dismiss", () -> panel.setVisible(false));
    return panel;
  }
}
