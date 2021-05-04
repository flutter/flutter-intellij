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
import io.flutter.FlutterBundle;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRoots;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FlutterCoverageEnabledConfiguration extends CoverageEnabledConfiguration {

  public FlutterCoverageEnabledConfiguration(@NotNull RunConfigurationBase<?> configuration) {
    super(configuration);
    setCoverageRunner(CoverageRunner.getInstance(FlutterCoverageRunner.class));
    final List<PubRoot> roots = PubRoots.forProject(configuration.getProject());
    if (roots.isEmpty()) {
      throw new RuntimeException(FlutterBundle.message("project.root.not.found"));
    }
    myCoverageFilePath = roots.get(0).getRoot().getCanonicalPath() + "/coverage/lcov.info";
    setCurrentCoverageSuite(CoverageDataManager.getInstance(configuration.getProject()).addCoverageSuite(this));
  }

  public void setCoverageRunner(@Nullable final CoverageRunner coverageRunner) {
    final String path = myCoverageFilePath;
    super.setCoverageRunner(coverageRunner);
    myCoverageFilePath = path;
  }

  public void coverageRunnerExtensionRemoved(@NotNull CoverageRunner runner) {
    final String path = myCoverageFilePath;
    super.coverageRunnerExtensionRemoved(runner);
    myCoverageFilePath = path;
  }
}
