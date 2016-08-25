/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspections;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.jetbrains.lang.dart.DartFileType;
import com.jetbrains.lang.dart.DartLanguage;
import com.jetbrains.lang.dart.sdk.DartSdk;
import io.flutter.FlutterBundle;
import io.flutter.module.FlutterModuleType;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkService;
import io.flutter.settings.FlutterSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WrongDartSdkConfigurationNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel>
  implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("Setup Dart SDK");

  private final Project project;

  public WrongDartSdkConfigurationNotificationProvider(@NotNull Project project, @NotNull EditorNotifications notifications) {
    this.project = project;
  }

  @NotNull
  private static EditorNotificationPanel createWrongSdkPanel(@NotNull Project project, @Nullable Module module) {

    final FlutterSettings settings = FlutterSettings.getInstance(project);
    if (settings.ignoreMismatchedDartSdks()) return null;

    EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText(FlutterBundle.message("flutter.wrong.dart.sdk.warning"));
    panel.createActionLabel(FlutterBundle.message("dart.sdk.configuration.action.label"),
                            () -> FlutterSdkService.getInstance(project).configureDartSdk(module));
    panel.createActionLabel("Dismiss", () -> {
      settings.setIgnoreMismatchedDartSdks(true);
      panel.setVisible(false);
    });

    return panel;
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    if (file.getFileType() != DartFileType.INSTANCE) return null;

    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile == null) return null;

    if (psiFile.getLanguage() != DartLanguage.INSTANCE) return null;

    Module module = ModuleUtilCore.findModuleForPsiElement(psiFile);
    if (module == null) return null;

    if (!ModuleType.is(module, FlutterModuleType.getInstance())) return null;

    try {
      final String flutterDartSdkPath = FlutterSdk.getFlutterSdk(project).getDartSdkPath();
      final String dartSdkPath = DartSdk.getDartSdk(project).getHomePath();
      if (!StringUtil.equals(flutterDartSdkPath, dartSdkPath)) {
        return createWrongSdkPanel(project, module);
      }
    }
    catch (ExecutionException e) {
      //TODO(pq): add panel for unconfigured Flutter SDK.
    }

    return null;
  }
}
