/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspections;

import com.intellij.ide.plugins.PluginManagerConfigurable;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.jetbrains.lang.dart.DartLanguage;
import io.flutter.FlutterBundle;
import io.flutter.FlutterUtils;
import io.flutter.dart.DartPlugin;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IncompatibleDartPluginNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {

  private static final Key<EditorNotificationPanel> KEY = Key.create("IncompatibleDartPluginNotificationProvider");

  private final Project myProject;

  public IncompatibleDartPluginNotificationProvider(@NotNull Project project) {
    this.myProject = project;
  }

  @Nullable
  private static EditorNotificationPanel createUpdateDartPanel(@NotNull Project project,
                                                               @Nullable Module module,
                                                               @NotNull String currentVersion,
                                                               @NotNull String minimumVersion) {
    if (module == null) return null;

    final EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText(FlutterBundle.message("flutter.incompatible.dart.plugin.warning", minimumVersion, currentVersion));
    panel.createActionLabel(FlutterBundle.message("dart.plugin.update.action.label"),
                            () -> ShowSettingsUtil.getInstance().showSettingsDialog(project, PluginManagerConfigurable.class));

    return panel;
  }

  private static String getPrintableRequiredDartVersion() {
    return DartPlugin.getInstance().getMinimumVersion().toCompactString();
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    if (!FlutterUtils.isFlutteryFile(file)) return null;

    final PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    if (psiFile == null) return null;

    if (psiFile.getLanguage() != DartLanguage.INSTANCE) return null;

    final Module module = ModuleUtilCore.findModuleForPsiElement(psiFile);
    if (module == null) return null;

    if (!FlutterModuleUtils.isFlutterModule(module)) return null;

    final Version minimumVersion = DartPlugin.getInstance().getMinimumVersion();
    final Version dartVersion = DartPlugin.getInstance().getVersion();
    return dartVersion.compareTo(minimumVersion) < 0 ? createUpdateDartPanel(myProject, module, dartVersion.toCompactString(),
                                                                             getPrintableRequiredDartVersion()) : null;
  }
}
