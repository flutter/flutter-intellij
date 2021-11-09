/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.jetbrains.lang.dart.psi.DartFile;
import io.flutter.FlutterUtils;
import io.flutter.bazel.Workspace;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.dart.DartPlugin;
import io.flutter.run.FlutterRunConfigurationProducer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Determines when we can run a Flutter test using "bazel test".
 */
public class BazelTestConfigProducer extends RunConfigurationProducer<BazelTestConfig> {

  private final BazelTestConfigUtils bazelTestConfigUtils;

  protected BazelTestConfigProducer() {
    this(FlutterBazelTestConfigurationType.getInstance().factory);
  }

  protected BazelTestConfigProducer(@NotNull ConfigurationFactory factory) {
    super(factory);
    bazelTestConfigUtils = BazelTestConfigUtils.getInstance();
  }

  @VisibleForTesting
  BazelTestConfigProducer(BazelTestConfigUtils bazelTestConfigUtils) {
    super(FlutterBazelTestConfigurationType.getInstance());
    this.bazelTestConfigUtils = bazelTestConfigUtils;
  }

  private boolean isBazelFlutterContext(@NotNull ConfigurationContext context) {
    final PsiElement location = context.getPsiLocation();
    return location != null && getWorkspace(context.getProject()) != null;
  }

  @VisibleForTesting
  @Nullable
  protected Workspace getWorkspace(@NotNull Project project) {
    return WorkspaceCache.getInstance(project).get();
  }

  /**
   * If the current file looks like a Flutter test, initializes the run config to run it.
   * <p>
   * Returns true if successfully set up.
   */
  @Override
  protected boolean setupConfigurationFromContext(@NotNull BazelTestConfig config,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
    if (!isBazelFlutterContext(context)) return false;

    final PsiElement elt = context.getPsiLocation();

    final DartFile file = FlutterUtils.getDartFile(elt);
    if (file == null) {
      return false;
    }

    final String testName = bazelTestConfigUtils.findTestName(elt);
    if (testName != null) {
      return setupForSingleTest(config, context, file, testName);
    }

    return setupForDartFile(config, context, file);
  }

  private boolean setupForSingleTest(
    @NotNull BazelTestConfig config, @NotNull ConfigurationContext context, @NotNull DartFile file, @NotNull String testName) {

    final VirtualFile testFile = verifyFlutterTestFile(config, context, file);
    if (testFile == null) return false;

    config.setFields(BazelTestFields.forTestName(testName, testFile.getPath(), config.getFields().getAdditionalArgs()));
    config.setGeneratedName();
    config.setName("Run '" + testName + "' in '" + file.getName() + "'");
    return true;
  }

  private boolean setupForDartFile(@NotNull BazelTestConfig config, @NotNull ConfigurationContext context, @NotNull DartFile file) {
    final VirtualFile testFile = verifyFlutterTestFile(config, context, file);
    if (testFile == null) return false;

    config.setFields(BazelTestFields.forFile(testFile.getPath(), config.getFields().getAdditionalArgs()));
    config.setName("Run '" + file.getName() + "'");

    return true;
  }

  @Nullable
  @VisibleForTesting
  VirtualFile verifyFlutterTestFile(@NotNull BazelTestConfig config,
                                    @NotNull ConfigurationContext context,
                                    @NotNull DartFile file) {
    final VirtualFile candidate = FlutterRunConfigurationProducer.getFlutterEntryFile(context, false, false);
    if (candidate == null) return null;

    return file.getVirtualFile().getPath().contains("/test/") ? candidate : null;
  }

  /**
   * Called on existing run configurations to check if one has already been created for the given {@param context}.
   *
   * @return true if a run config was already created for this context. If so we will reuse it.
   */
  @Override
  public boolean isConfigurationFromContext(@NotNull BazelTestConfig config, @NotNull ConfigurationContext context) {
    // Check if the config is a non-watch producer and the producer is a watch producer or vice versa, then the given configuration
    // is not a replacement for one by this producer.
    if (!StringUtil.equals(getId(config), getConfigurationFactory().getId())) return false;

    final VirtualFile file = config.getFields().getFile();
    if (file == null) return false;

    final PsiElement target = context.getPsiLocation();
    if (target instanceof PsiDirectory) {
      return ((PsiDirectory)target).getVirtualFile().equals(file);
    }

    if (!FlutterRunConfigurationProducer.hasDartFile(context, file.getPath())) return false;

    final String testName = bazelTestConfigUtils.findTestName(context.getPsiLocation());
    if (config.getFields().getTestName() != null) {
      return testName != null && testName.equals(config.getFields().getTestName());
    }
    return testName == null;
  }

  @Override
  public boolean shouldReplace(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return DartPlugin.isDartTestConfiguration(other.getConfigurationType());
  }

  @NotNull
  private String getId(BazelTestConfig config) {
    return config.getFields().isWatchConfig() ? "Watch" : "No Watch";
  }
}
