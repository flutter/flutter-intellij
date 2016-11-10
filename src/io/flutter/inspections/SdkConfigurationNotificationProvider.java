/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspections;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
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
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkService;
import io.flutter.sdk.FlutterSdkUtil;
import io.flutter.sdk.FlutterSdkVersion;
import io.flutter.settings.FlutterSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SdkConfigurationNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel>
  implements DumbAware {

  // Minimum SDK known to support hot reload.
  private static final FlutterSdkVersion MIN_SUPPORTED_SDK = FlutterSdkVersion.forVersionString("0.0.3");

  private static final Key<EditorNotificationPanel> KEY = Key.create("FlutterWrongDartSdkNotification");

  private static final Logger LOG = Logger.getInstance(SdkConfigurationNotificationProvider.class);

  private final Project project;

  public SdkConfigurationNotificationProvider(@NotNull Project project) {
    this.project = project;
  }

  @Nullable
  private static EditorNotificationPanel createWrongSdkPanel(@NotNull Project project, @Nullable Module module) {
    if (module == null) return null;

    final FlutterSettings settings = FlutterSettings.getInstance();
    if (settings.shouldIgnoreMismatchedDartSdks()) return null;

    EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText(FlutterBundle.message("flutter.wrong.dart.sdk.warning"));
    panel.createActionLabel(FlutterBundle.message("dart.sdk.configuration.action.label"),
                            () -> FlutterSdkService.getInstance(project).configureDartSdk(module));
    panel.createActionLabel("Dismiss", () -> {
      settings.setIgnoreMismatchedDartSdks();
      panel.setVisible(false);
    });

    return panel;
  }

  private static EditorNotificationPanel createNoFlutterSdkPanel() {
    // TODO(pq): add panel for unconfigured Flutter SDK.

    return null;
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

    if (!FlutterSdkUtil.isFlutterModule(module)) return null;

    try {
      final FlutterSdk flutterSdk = FlutterSdk.getFlutterSdk(project);
      if (flutterSdk == null) {
        return createNoFlutterSdkPanel();
      }

      if (flutterSdk.getVersion().isLessThan(MIN_SUPPORTED_SDK)) {
        return createOutOfDateFlutterSdkPanel(flutterSdk);
      }

      DartSdk dartSdk = DartSdk.getDartSdk(project);
      if (dartSdk == null) {
        // TODO(devoncarew): Recommend to set up with Flutter's dart sdk.

        return null;
      }

      final String flutterDartSdkPath = flutterSdk.getDartSdkPath();
      final String dartSdkPath = dartSdk.getHomePath();
      if (!StringUtil.equals(flutterDartSdkPath, dartSdkPath)) {
        return createWrongSdkPanel(project, module);
      }
    }
    catch (ExecutionException e) {
      LOG.warn(e);
    }

    return null;
  }

  private EditorNotificationPanel createOutOfDateFlutterSdkPanel(@NotNull FlutterSdk sdk) {

    final FlutterSettings settings = FlutterSettings.getInstance();
    if (settings.shouldIgnoreOutOfDateFlutterSdks()) return null;

    EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText(FlutterBundle.message("flutter.old.sdk.warning"));
    panel.createActionLabel("Dismiss", () -> {
      settings.setIgnoreOutOfDateFlutterSdks();
      panel.setVisible(false);
    });

    return panel;
  }
}
