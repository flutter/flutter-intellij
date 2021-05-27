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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.GuiUtils;
import io.flutter.FlutterBundle;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRoots;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FlutterCoverageEnabledConfiguration extends CoverageEnabledConfiguration {
  private static final Logger LOG = Logger.getInstance(FlutterCoverageEnabledConfiguration.class.getName());

  public FlutterCoverageEnabledConfiguration(@NotNull RunConfigurationBase<?> configuration) {
    super(configuration);
    super.setCoverageRunner(CoverageRunner.getInstance(FlutterCoverageRunner.class));
    createCoverageFile();
    GuiUtils.invokeLaterIfNeeded(
      () -> setCurrentCoverageSuite(CoverageDataManager.getInstance(configuration.getProject()).addCoverageSuite(this)),
      ModalityState.any());
  }

  @Override
  protected String createCoverageFile() {
    if (myCoverageFilePath == null) {
      final List<PubRoot> roots = PubRoots.forProject(getConfiguration().getProject());
      if (roots.isEmpty()) {
        throw new RuntimeException(FlutterBundle.message("project.root.not.found"));
      }
      final VirtualFile root = roots.get(0).getRoot();
      myCoverageFilePath = root.getPath() + "/coverage/lcov.info";
    }
    return myCoverageFilePath;
  }

  @Override
  public void setCoverageRunner(@Nullable final CoverageRunner coverageRunner) {
    // Save and restore myCoverageFilePath because the super method clears it.
    final String path = myCoverageFilePath;
    super.setCoverageRunner(coverageRunner);
    myCoverageFilePath = path;
  }

  @Override
  public void coverageRunnerExtensionRemoved(@NotNull CoverageRunner runner) {
    final String path = myCoverageFilePath;
    super.coverageRunnerExtensionRemoved(runner);
    myCoverageFilePath = path;
  }
}
