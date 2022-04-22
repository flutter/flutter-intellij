/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.coverage;

import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.coverage.SimpleCoverageAnnotator;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FlutterCoverageAnnotator extends SimpleCoverageAnnotator {

  @Nullable
  public static FlutterCoverageAnnotator getInstance(Project project) {
    return project.getService(FlutterCoverageAnnotator.class);
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
  protected VirtualFile[] getRoots(Project project,
                                   @NotNull CoverageDataManager dataManager,
                                   CoverageSuitesBundle suite) {
    return dataManager.doInReadActionIfProjectOpen(() -> {
      final List<VirtualFile> roots = new ArrayList<>();
      for (Module module : FlutterModuleUtils.findModulesWithFlutterContents(project)) {
        final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        roots.addAll(Arrays.asList(rootManager.getContentRoots()));
      }
      return roots.toArray(VirtualFile.EMPTY_ARRAY);
    });
  }
}
