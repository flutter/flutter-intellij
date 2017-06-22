/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspections;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.jetbrains.lang.dart.DartFileType;
import com.jetbrains.lang.dart.DartLanguage;
import io.flutter.FlutterBundle;
import io.flutter.bazel.Workspace;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.sdk.FlutterSdk;
import io.flutter.settings.FlutterUIConfig;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;

public class SdkConfigurationNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel>
  implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("FlutterWrongDartSdkNotification");

  private static final Logger LOG = Logger.getInstance(SdkConfigurationNotificationProvider.class);

  @NotNull private final Project project;

  public SdkConfigurationNotificationProvider(@NotNull Project project) {
    this.project = project;
  }

  @SuppressWarnings("SameReturnValue")
  private static EditorNotificationPanel createNoFlutterSdkPanel() {
    final EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText(FlutterBundle.message("flutter.no.sdk.warning"));
    panel.createActionLabel("Dismiss", () -> panel.setVisible(false));

    // TODO(skybrian) we should add a link to go to the Dart SDK panel.
    // However, not yet because this will change with 2017.1 to be project-specific.

    return panel;
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull final VirtualFile file, @NotNull final FileEditor fileEditor) {

    // If this is a Bazel configured Flutter project, exit immediately, neither of the notifications should be shown for this project type.
    final Workspace workspace = WorkspaceCache.getInstance(project).getNow();
    if (workspace != null && workspace.usesFlutter(project)) {
      return null;
    }

    if (file.getFileType() != DartFileType.INSTANCE) return null;

    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile == null || psiFile.getLanguage() != DartLanguage.INSTANCE) return null;

    final Module module = ModuleUtilCore.findModuleForPsiElement(psiFile);
    if (module == null || !FlutterModuleUtils.isFlutterModule(module)) return null;

    final FlutterSdk flutterSdk = FlutterSdk.getFlutterSdk(project);
    if (flutterSdk == null) {
      return createNoFlutterSdkPanel();
    }
    else if (!flutterSdk.getVersion().isSupported()) {
      return createOutOfDateFlutterSdkPanel(flutterSdk);
    }

    return null;
  }

  private EditorNotificationPanel createOutOfDateFlutterSdkPanel(@NotNull FlutterSdk sdk) {

    final FlutterUIConfig settings = FlutterUIConfig.getInstance();
    if (settings.shouldIgnoreOutOfDateFlutterSdks()) return null;

    final EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText(FlutterBundle.message("flutter.old.sdk.warning"));
    panel.createActionLabel("Dismiss", () -> {
      settings.setIgnoreOutOfDateFlutterSdks();
      panel.setVisible(false);
    });

    return panel;
  }
}
