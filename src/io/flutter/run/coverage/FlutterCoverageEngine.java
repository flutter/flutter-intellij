/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.coverage;

import com.intellij.coverage.*;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.WrappingRunConfiguration;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.jetbrains.lang.dart.DartFileType;
import com.jetbrains.lang.dart.psi.DartFile;
import io.flutter.FlutterBundle;
import io.flutter.FlutterUtils;
import io.flutter.pub.PubRoot;
import io.flutter.run.test.TestConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class FlutterCoverageEngine extends CoverageEngine {

  public static FlutterCoverageEngine getInstance() {
    return CoverageEngine.EP_NAME.findExtensionOrFail(FlutterCoverageEngine.class);
  }

  @Override
  public boolean isApplicableTo(@NotNull RunConfigurationBase conf) {
    return unwrapRunProfile(conf) instanceof TestConfig;
  }

  @Override
  public boolean canHavePerTestCoverage(@NotNull RunConfigurationBase conf) {
    return true;
  }

  @Override
  public @NotNull CoverageEnabledConfiguration createCoverageEnabledConfiguration(@NotNull RunConfigurationBase conf) {
    return new FlutterCoverageEnabledConfiguration(conf);
  }

  @Override
  @SuppressWarnings("deprecation")
  public @Nullable CoverageSuite createCoverageSuite(@NotNull CoverageRunner covRunner,
                                                     @NotNull String name,
                                                     @NotNull CoverageFileProvider coverageDataFileProvider,
                                                     @Nullable String[] filters,
                                                     long lastCoverageTimeStamp,
                                                     @Nullable String suiteToMerge,
                                                     boolean coverageByTestEnabled,
                                                     boolean tracingEnabled,
                                                     boolean trackTestFolders,
                                                     Project project) {
    return null;
  }

  @Override
  @SuppressWarnings("deprecation")
  public @Nullable CoverageSuite createCoverageSuite(@NotNull CoverageRunner covRunner,
                                                     @NotNull String name,
                                                     @NotNull CoverageFileProvider coverageDataFileProvider,
                                                     @NotNull CoverageEnabledConfiguration config) {
    if (config instanceof FlutterCoverageEnabledConfiguration) {
      return new FlutterCoverageSuite(covRunner, name, coverageDataFileProvider,
                                      config.getConfiguration().getProject(), this);
    }
    return null;
  }

  @Override
  public @Nullable CoverageSuite createEmptyCoverageSuite(@NotNull CoverageRunner coverageRunner) {
    return new FlutterCoverageSuite(this);
  }

  @Override
  public @NotNull CoverageAnnotator getCoverageAnnotator(Project project) {
    return FlutterCoverageAnnotator.getInstance(project);
  }

  @Override
  public boolean coverageEditorHighlightingApplicableTo(@NotNull PsiFile psiFile) {
    final PubRoot root = PubRoot.forPsiFile(psiFile);
    if (root == null) return false;
    final VirtualFile file = psiFile.getVirtualFile();
    if (file == null) return false;
    final String path = root.getRelativePath(file);
    if (path == null) return false;
    return path.startsWith("lib") && FlutterUtils.isDartFile(file);
  }

  @Override
  public boolean coverageProjectViewStatisticsApplicableTo(VirtualFile fileOrDir) {
    return !fileOrDir.isDirectory() && fileOrDir.getFileType() instanceof DartFileType;
  }

  @Override
  public boolean acceptedByFilters(@NotNull PsiFile psiFile, @NotNull CoverageSuitesBundle suite) {
    return psiFile instanceof DartFile;
  }

  @Override
  public boolean recompileProjectAndRerunAction(@NotNull Module module,
                                                @NotNull CoverageSuitesBundle suite,
                                                @NotNull Runnable chooseSuiteAction) {
    return false;
  }

  @Override
  public String getQualifiedName(@NotNull final File outputFile,
                                 @NotNull final PsiFile sourceFile) {
    return getQName(sourceFile);
  }

  @Override
  public @NotNull Set<String> getQualifiedNames(@NotNull PsiFile sourceFile) {
    final Set<String> qualifiedNames = new HashSet<>();
    qualifiedNames.add(getQName(sourceFile));
    return qualifiedNames;
  }

  @Override
  public String getPresentableText() {
    return FlutterBundle.message("flutter.coverage.presentable.text");
  }

  @NotNull
  private static String getQName(@NotNull PsiFile sourceFile) {
    return sourceFile.getVirtualFile().getPath();
  }

  static @NotNull RunProfile unwrapRunProfile(@NotNull RunProfile runProfile) {
    if (runProfile instanceof WrappingRunConfiguration) {
      return ((WrappingRunConfiguration<?>)runProfile).getPeer();
    }
    return runProfile;
  }
}