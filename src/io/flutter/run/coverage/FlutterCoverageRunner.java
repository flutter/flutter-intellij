/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.coverage;

import com.intellij.coverage.CoverageEngine;
import com.intellij.coverage.CoverageLoadErrorReporter;
import com.intellij.coverage.CoverageLoadingResult;
import com.intellij.coverage.CoverageRunner;
import com.intellij.coverage.CoverageSuite;
import com.intellij.coverage.FailedCoverageLoadingResult;
import com.intellij.coverage.SuccessCoverageLoadingResult;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.rt.coverage.data.ProjectData;
import io.flutter.FlutterBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class FlutterCoverageRunner extends CoverageRunner {
  private static final String ID = "FlutterCoverageRunner";
  private static final @NotNull Logger LOG = Logger.getInstance(FlutterCoverageRunner.class.getName());

  @Override
  public @NotNull CoverageLoadingResult loadCoverageData(@NotNull final File sessionDataFile,
                                                         @Nullable CoverageSuite baseCoverageSuite,
                                                         @NotNull CoverageLoadErrorReporter reporter) {
    if (!(baseCoverageSuite instanceof FlutterCoverageSuite)) {
      return new FailedCoverageLoadingResult("Flutter coverage suite is not a FlutterCoverageSuite");
    }
    return doLoadCoverageData(sessionDataFile, (FlutterCoverageSuite)baseCoverageSuite);
  }

  private static @NotNull CoverageLoadingResult doLoadCoverageData(@NotNull final File sessionDataFile,
                                                                   @NotNull final FlutterCoverageSuite coverageSuite) {
    final ProjectData projectData = new ProjectData();
    try {
      LcovInfo.readInto(projectData, sessionDataFile);
    }
    catch (IOException ex) {
      LOG.warn(FlutterBundle.message("coverage.data.not.read", sessionDataFile.getAbsolutePath()));
    }
    if (projectData == null) {
      return new FailedCoverageLoadingResult("Flutter coverage data could not be read");
    }
    return new SuccessCoverageLoadingResult(projectData);
  }

  @NotNull
  @Override
  public String getPresentableName() {
    return "Flutter";
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  @NotNull
  @Override
  public String getDataFileExtension() {
    return "info";
  }

  @Override
  public boolean acceptsCoverageEngine(@NotNull CoverageEngine engine) {
    return engine instanceof FlutterCoverageEngine;
  }
}
