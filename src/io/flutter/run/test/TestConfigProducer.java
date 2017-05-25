/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.test;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import io.flutter.dart.DartPlugin;
import io.flutter.run.FlutterRunConfigurationProducer;
import org.jetbrains.annotations.NotNull;

/**
 * Determines when we can run a test using "flutter test".
 */
public class TestConfigProducer extends RunConfigurationProducer<TestConfig> {

  protected TestConfigProducer() {
    super(TestConfigType.getInstance());
  }

  /**
   * If the current file looks like a Flutter test, initializes the run config to run it.
   */
  @Override
  protected boolean setupConfigurationFromContext(TestConfig config, ConfigurationContext context, Ref<PsiElement> sourceElement) {
    final VirtualFile candidate = FlutterRunConfigurationProducer.getFlutterEntryFile(context, false);
    if (candidate == null || !candidate.getName().endsWith("_test.dart")) {
      return false;
    }

    config.setFields(new TestFields(candidate.getPath()));
    config.setGeneratedName();

    return true;
  }

  /**
   * Returns true if a run config was already created for this file. If so we will reuse it.
   */
  @Override
  public boolean isConfigurationFromContext(TestConfig config, ConfigurationContext context) {
    final String testFile = config.getFields().getTestFile();
    return FlutterRunConfigurationProducer.hasDartFile(context, testFile);
  }

  @Override
  public boolean shouldReplace(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return DartPlugin.isDartTestConfiguration(other.getConfigurationType());
  }
}
