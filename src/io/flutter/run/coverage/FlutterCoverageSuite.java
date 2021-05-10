/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.coverage;

import com.intellij.coverage.BaseCoverageSuite;
import com.intellij.coverage.CoverageEngine;
import com.intellij.coverage.CoverageFileProvider;
import com.intellij.coverage.CoverageRunner;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterCoverageSuite extends BaseCoverageSuite {

  @NotNull
  final private FlutterCoverageEngine coverageEngine;

  public FlutterCoverageSuite(@NotNull FlutterCoverageEngine coverageEngine) {
    this.coverageEngine = coverageEngine;
  }

  public FlutterCoverageSuite(CoverageRunner runner,
                              String name,
                              CoverageFileProvider coverageDataFileProvider,
                              Project project,
                              @NotNull FlutterCoverageEngine coverageEngine
  ) {
    super(name, coverageDataFileProvider, System.currentTimeMillis(), false, false,
          false, runner, project);
    this.coverageEngine = coverageEngine;
  }

  @Override
  public @NotNull CoverageEngine getCoverageEngine() {
    return coverageEngine;
  }
}
