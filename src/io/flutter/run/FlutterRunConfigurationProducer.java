/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.jetbrains.lang.dart.ide.DartWritingAccessProvider;
import com.jetbrains.lang.dart.psi.DartFile;
import com.jetbrains.lang.dart.psi.DartImportStatement;
import com.jetbrains.lang.dart.util.DartResolveUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterRunConfigurationProducer extends RunConfigurationProducer<FlutterRunConfiguration> {

  public FlutterRunConfigurationProducer() {
    super(FlutterRunConfigurationType.getInstance());
  }

  @Override
  protected boolean setupConfigurationFromContext(final @NotNull FlutterRunConfiguration configuration,
                                                  final @NotNull ConfigurationContext context,
                                                  final @NotNull Ref<PsiElement> sourceElement) {
    final VirtualFile dartFile = getRunnableFlutterFileFromContext(context);
    if (dartFile != null) {
      configuration.getRunnerParameters().setFilePath(dartFile.getPath());
      configuration.getRunnerParameters()
        .setWorkingDirectory(FlutterRunnerParameters.suggestDartWorkingDir(context.getProject(), dartFile));
      configuration.setGeneratedName();

      sourceElement.set(sourceElement.isNull() ? null : sourceElement.get().getContainingFile());
      return true;
    }
    return false;
  }

  @Override
  public boolean isConfigurationFromContext(final @NotNull FlutterRunConfiguration configuration,
                                            final @NotNull ConfigurationContext context) {
    final VirtualFile dartFile = getDartFileFromContext(context);
    return dartFile != null && dartFile.getPath().equals(configuration.getRunnerParameters().getFilePath());
  }

  @Nullable
  private static VirtualFile getRunnableFlutterFileFromContext(final @NotNull ConfigurationContext context) {
    final PsiElement psiLocation = context.getPsiLocation();
    final PsiFile psiFile = psiLocation == null ? null : psiLocation.getContainingFile();
    final VirtualFile virtualFile = DartResolveUtil.getRealVirtualFile(psiFile);

    if (psiFile instanceof DartFile &&
        virtualFile != null &&
        ProjectRootManager.getInstance(context.getProject()).getFileIndex().isInContent(virtualFile) &&
        !DartWritingAccessProvider.isInDartSdkOrDartPackagesFolder(psiFile.getProject(), virtualFile) &&
        DartResolveUtil.getMainFunction(psiFile) != null &&
        hasImport((DartFile)psiFile, "package:flutter/material.dart")) { // TODO Determine flutter imports
      return virtualFile;
    }

    return null;
  }

  @Nullable
  private static VirtualFile getDartFileFromContext(final @NotNull ConfigurationContext context) {
    final PsiElement psiLocation = context.getPsiLocation();
    final PsiFile psiFile = psiLocation == null ? null : psiLocation.getContainingFile();
    final VirtualFile virtualFile = DartResolveUtil.getRealVirtualFile(psiFile);
    return psiFile instanceof DartFile && virtualFile != null ? virtualFile : null;
  }

  private static boolean hasImport(final @NotNull DartFile psiFile, final @NotNull String... importTexts) {
    final DartImportStatement[] importStatements = PsiTreeUtil.getChildrenOfType(psiFile, DartImportStatement.class);
    if (importStatements == null) return false;

    for (DartImportStatement importStatement : importStatements) {
      if (ArrayUtil.contains(importStatement.getUriString(), importTexts)) return true;
    }

    return false;
  }
}
