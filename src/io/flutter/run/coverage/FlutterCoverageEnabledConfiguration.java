/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.coverage;

import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageRunner;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.openapi.application.ModalityState;
import com.intellij.util.ModalityUiUtil;
import io.flutter.pub.PubRoot;
import io.flutter.run.test.TestConfig;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class FlutterCoverageEnabledConfiguration extends CoverageEnabledConfiguration {

  public FlutterCoverageEnabledConfiguration(@NotNull RunConfigurationBase<?> configuration) {
    super(configuration, Objects.requireNonNull(CoverageRunner.getInstance(FlutterCoverageRunner.class)));
    ModalityUiUtil.invokeLaterIfNeeded(
      ModalityState.any(),
      () -> setCurrentCoverageSuite(CoverageDataManager.getInstance(configuration.getProject()).addCoverageSuite(this)));
  }

  @Override
  protected String createCoverageFile() {
    if (!(FlutterCoverageEngine.unwrapRunProfile(getConfiguration()) instanceof TestConfig testConfig)) {
      return "";
    }
    final PubRoot pubRoot = testConfig.getFields().getPubRoot(testConfig.getProject());
    if (pubRoot == null) {
      return "";
    }
    return pubRoot.getRoot().getPath() + "/coverage/lcov.info";
  }
}