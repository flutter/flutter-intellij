/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.coverage;

import com.intellij.coverage.CoverageBundle;
import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.coverage.SimpleCoverageAnnotator;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterBundle;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FlutterCoverageAnnotator extends SimpleCoverageAnnotator {

  public static FlutterCoverageAnnotator getInstance(Project project) {
    return ServiceManager.getService(project, FlutterCoverageAnnotator.class);
  }

  public FlutterCoverageAnnotator(Project project) {
    super(project);
  }

  @Override
  protected FileCoverageInfo fillInfoForUncoveredFile(@NotNull File file) {
    return new FileCoverageInfo();
  }

  @Override
  protected boolean shouldCollectCoverageInsideLibraryDirs() {
    return false;
  }

  @Override
  protected String getLinesCoverageInformationString(@NotNull FileCoverageInfo info) {
    if (info.totalLineCount == 0) {
      return null;
    }
    else if (info.coveredLineCount == 0) {
      return info instanceof DirCoverageInfo ? null : CoverageBundle.message("lines.covered.info.no.lines.covered");
    }
    else if (info.coveredLineCount * 100 < info.totalLineCount) {
      return CoverageBundle.message("lines.covered.info.less.than.one.percent");
    }
    else {
      String percent = String.valueOf(calcCoveragePercentage(info));
      return percent + CoverageBundle.message("lines.covered.info.percent.lines.covered");
    }
  }

  protected String getFilesCoverageInformationString(@NotNull DirCoverageInfo info) {
    if (info.totalFilesCount == 0) {
      return null;
    }
    else {
      return info.coveredFilesCount == 0
             ? FlutterBundle.message("coverage.string.0.of.1.files.covered", info.coveredFilesCount, info.totalFilesCount)
             : FlutterBundle.message("coverage.string.0.of.1.files", info.coveredFilesCount, info.totalFilesCount);
    }
  }

  protected VirtualFile[] getRoots(Project project,
                                   @NotNull CoverageDataManager dataManager,
                                   CoverageSuitesBundle suite) {
    return dataManager.doInReadActionIfProjectOpen(() -> {
      final List<VirtualFile> roots = new ArrayList<>();
      for (Module module : FlutterModuleUtils.findModulesWithFlutterContents(project)) {
        final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        roots.addAll(Arrays.asList(rootManager.getSourceRoots()));
      }
      return roots.toArray(new VirtualFile[0]);
    });
  }
}
