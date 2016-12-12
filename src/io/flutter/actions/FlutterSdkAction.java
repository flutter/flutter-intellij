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
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import io.flutter.FlutterBundle;
import io.flutter.FlutterConstants;
import io.flutter.FlutterErrors;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for Flutter commands.
 */
public abstract class FlutterSdkAction extends DumbAwareAction {

  private static final Logger LOG = Logger.getInstance(FlutterSdkAction.class);

  @Nullable
  public static Pair<Module, VirtualFile> getModuleAndPubspecYamlFile(final Project project, final AnActionEvent e) {
    Module module = LangDataKeys.MODULE.getData(e.getDataContext());
    final PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(e.getDataContext());

    VirtualFile pubspec = findPubspecFrom(project, psiFile);
    if (pubspec == null) {
      pubspec = findPubspecFrom(module);
    }

    if (module == null && pubspec != null) {
      module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(pubspec);
    }

    return pubspec == null ? null : Pair.create(module, pubspec);
  }

  protected static VirtualFile findPubspecFrom(Project project, PsiFile psiFile) {
    if (psiFile == null) {
      return null;
    }
    final VirtualFile file = psiFile.getVirtualFile();
    final VirtualFile contentRoot = ProjectRootManager.getInstance(project).getFileIndex().getContentRootForFile(file);
    return contentRoot == null ? null : contentRoot.findChild(FlutterConstants.PUBSPEC_YAML);
  }

  protected static VirtualFile findPubspecFrom(Module module) {
    if (module == null) {
      return null;
    }
    final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    for (VirtualFile dir : moduleRootManager.getContentRoots()) {
      final VirtualFile pubspec = dir.findChild(FlutterConstants.PUBSPEC_YAML);
      if (pubspec != null) {
        return pubspec;
      }
    }
    return null;
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    final Project project = DumbAwareAction.getEventProject(event);
    final FlutterSdk sdk = project != null ? FlutterSdk.getFlutterSdk(project) : null;

    if (sdk != null) {
      try {
        perform(sdk, project, event);
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
        FlutterBundle.message("flutter.sdk.notAvailable.title"),
        FlutterBundle.message("flutter.sdk.notAvailable.message"));
    }
  }

  public abstract void perform(@NotNull FlutterSdk sdk, @NotNull Project project, AnActionEvent event) throws ExecutionException;
}
