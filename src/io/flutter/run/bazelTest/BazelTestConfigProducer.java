/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.jetbrains.lang.dart.psi.DartFile;
import com.sun.corba.se.spi.orbutil.threadpool.Work;
import io.flutter.FlutterUtils;
import io.flutter.bazel.Workspace;
import io.flutter.dart.DartPlugin;
import io.flutter.run.FlutterRunConfigurationProducer;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Determines when we can run a Flutter test using "bazel test".
 */
public class BazelTestConfigProducer extends RunConfigurationProducer<BazelTestConfig> {

  private final BazelTestConfigUtils bazelTestConfigUtils = BazelTestConfigUtils.getInstance();

  protected BazelTestConfigProducer() {
    super(FlutterBazelTestConfigurationType.getInstance());
  }

  private static boolean isFlutterContext(@NotNull ConfigurationContext context) {
    final PsiElement location = context.getPsiLocation();
    return location != null;
  }

  /**
   * If the current file looks like a Flutter test, initializes the run config to run it.
   * <p>
   * Returns true if successfully set up.
   */
  @Override
  protected boolean setupConfigurationFromContext(@NotNull BazelTestConfig config, @NotNull ConfigurationContext context, @NotNull Ref<PsiElement> sourceElement) {
    if (!isFlutterContext(context)) return false;

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

    config.setFields(BazelTestFields.forTestName(testName, testFile.getPath()));
    config.setGeneratedName();
    config.setName(file.getName() + " (" + testName + ")");
    return true;
  }

  private boolean setupForDartFile(@NotNull BazelTestConfig config, @NotNull ConfigurationContext context, @NotNull DartFile file) {
    final VirtualFile testFile = verifyFlutterTestFile(config, context, file);
    if (testFile == null) return false;

    config.setFields(BazelTestFields.forFile(testFile.getPath()));
    config.setName(file.getName());

    return true;
  }

  @Nullable
  private VirtualFile verifyFlutterTestFile(@NotNull BazelTestConfig config, @NotNull ConfigurationContext context, @NotNull DartFile file) {
      final VirtualFile candidate = FlutterRunConfigurationProducer.getFlutterEntryFile(context, false, false);
    if (candidate == null) return null;

    return file.getVirtualFile().getPath().contains("/test/") ?  candidate : null;
  }

  /**
   * Returns true if a run config was already created for this file. If so we will reuse it.
   */
  @Override
  public boolean isConfigurationFromContext(@NotNull BazelTestConfig config, @NotNull ConfigurationContext context) {
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
    else {
      return testName == null;
    }
  }

  @Override
  public boolean shouldReplace(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return DartPlugin.isDartTestConfiguration(other.getConfigurationType());
  }
}
