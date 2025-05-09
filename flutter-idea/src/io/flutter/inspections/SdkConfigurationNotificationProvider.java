/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspections;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.jetbrains.lang.dart.DartLanguage;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.FlutterUtils;
import io.flutter.sdk.FlutterSdk;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

public class SdkConfigurationNotificationProvider implements EditorNotificationProvider {

  @NotNull
  private final Project project;

  public SdkConfigurationNotificationProvider(@NotNull Project project) {
    this.project = project;
  }

  @Override
  public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                 @NotNull VirtualFile file) {
    // If this is a Bazel configured Flutter project, exit immediately, neither of the notifications should be shown for this project type.
    if (FlutterModuleUtils.isFlutterBazelProject(project)) return null;

    if (!FlutterUtils.isDartFile(file)) return null;

    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile == null || psiFile.getLanguage() != DartLanguage.INSTANCE) return null;

    final Module module = ModuleUtilCore.findModuleForPsiElement(psiFile);
    if (!FlutterModuleUtils.isFlutterModule(module)) return null;

    final FlutterSdk flutterSdk = FlutterSdk.getFlutterSdk(project);
    if (flutterSdk == null) {
      return fileEditor -> createNoFlutterSdkPanel(fileEditor, project);
    }
    return null;
  }

  private static EditorNotificationPanel createNoFlutterSdkPanel(@NotNull FileEditor fileEditor, @NotNull Project project) {
    final EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.icon(FlutterIcons.Flutter);
    panel.setText(FlutterBundle.message("flutter.no.sdk.warning"));
    panel.createActionLabel("Dismiss", () -> panel.setVisible(false));
    panel.createActionLabel("Open Flutter settings", () -> FlutterUtils.openFlutterSettings(project));
    return panel;
  }
}
