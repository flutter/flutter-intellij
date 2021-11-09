/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.coverage;

import com.intellij.coverage.CoverageEngine;
import com.intellij.coverage.CoverageRunner;
import com.intellij.coverage.CoverageSuite;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.rt.coverage.data.ProjectData;
import io.flutter.FlutterBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class FlutterCoverageRunner extends CoverageRunner {
  private static final String ID = "FlutterCoverageRunner";
  private static final Logger LOG = Logger.getInstance(FlutterCoverageRunner.class.getName());

  @Nullable
  @Override
  public ProjectData loadCoverageData(@NotNull final File sessionDataFile, @Nullable CoverageSuite baseCoverageSuite) {
    if (!(baseCoverageSuite instanceof FlutterCoverageSuite)) {
      return null;
    }
    return doLoadCoverageData(sessionDataFile, (FlutterCoverageSuite)baseCoverageSuite);
  }

  @Nullable
  private static ProjectData doLoadCoverageData(@NotNull final File sessionDataFile, @NotNull final FlutterCoverageSuite coverageSuite) {
    final ProjectData projectData = new ProjectData();
    try {
      LcovInfo.readInto(projectData, sessionDataFile);
    }
    catch (IOException ex) {
      LOG.warn(FlutterBundle.message("coverage.data.not.read", sessionDataFile.getAbsolutePath()));
      return null;
    }
    return projectData;
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

