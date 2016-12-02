/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import io.flutter.FlutterBundle;
import io.flutter.FlutterErrors;
import io.flutter.inspections.FlutterYamlNotificationProvider;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.Nullable;

public class FlutterUpgradeAction extends DumbAwareAction {
  private static final Logger LOG = Logger.getInstance(FlutterUpgradeAction.class);

  @Override
  public void actionPerformed(AnActionEvent event) {
    final Project project = DumbAwareAction.getEventProject(event);
    final FlutterSdk sdk = project != null ? FlutterSdk.getFlutterSdk(project) : null;

    if (sdk != null) {
      final Pair<Module, VirtualFile> pair = getModuleAndPubspecYamlFile(event);
      if (pair != null) {
        try {
          sdk.run(FlutterSdk.Command.UPGRADE, pair.first, pair.second.getParent(), null);
        }
        catch (ExecutionException e) {
          FlutterErrors.showError(
            FlutterBundle.message("flutter.command.exception.title"),
            FlutterBundle.message("flutter.command.exception.message", e.getMessage()));
          LOG.warn(e);
        }
      }
      else {
        FlutterErrors.showError(
          FlutterBundle.message("flutter.command.exception.title"),
          FlutterBundle.message("flutter.command.upgrade.missing.pubspec.message", project.getName()));
      }
    }
    else {
      FlutterErrors.showError(
        FlutterBundle.message("flutter.sdk.notAvailable.title"),
        FlutterBundle.message("flutter.sdk.notAvailable.message"));
    }
  }

  @Nullable
  private static Pair<Module, VirtualFile> getModuleAndPubspecYamlFile(final AnActionEvent e) {
    final Module module = LangDataKeys.MODULE.getData(e.getDataContext());
    final PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(e.getDataContext());

    if (module != null && psiFile != null && psiFile.getName().equalsIgnoreCase(FlutterYamlNotificationProvider.FLUTTER_YAML_NAME)) {
      final VirtualFile file = psiFile.getOriginalFile().getVirtualFile();
      return file != null ? Pair.create(module, file) : null;
    }

    return null;
  }
}
