/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.intellij.ide.util.importProject.ModuleDescriptor;
import com.intellij.ide.util.importProject.ProjectDescriptor;
import com.intellij.ide.util.projectWizard.importSources.DetectedProjectRoot;
import com.intellij.ide.util.projectWizard.importSources.ProjectFromSourcesBuilder;
import com.intellij.ide.util.projectWizard.importSources.ProjectStructureDetector;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.pub.PubRoot;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


public class FlutterProjectStructureDetector extends ProjectStructureDetector {
  @NotNull
  @Override
  public DirectoryProcessingResult detectRoots(@NotNull File parent,
                                               @NotNull File[] children,
                                               @NotNull File base,
                                               @NotNull List<DetectedProjectRoot> result) {
    final VirtualFile dir = VfsUtil.findFileByIoFile(parent, false);
    if (dir != null) {
      final PubRoot pubRoot = PubRoot.forDirectory(dir);
      if (pubRoot != null) {
        if (pubRoot.declaresFlutter()) {
          result.add(new FlutterProjectRoot(parent));
        } else {
          // TODO(pq): consider pushing pure dart project detection down into the Dart Plugin.
          result.add(new DartProjectRoot(parent));
        }
        return DirectoryProcessingResult.SKIP_CHILDREN;
      }
    }
    return DirectoryProcessingResult.PROCESS_CHILDREN;
  }

  @Override
  public String getDetectorId() {
    return "Flutter";
  }

  @Override
  public void setupProjectStructure(@NotNull Collection<DetectedProjectRoot> roots,
                                    @NotNull ProjectDescriptor projectDescriptor,
                                    @NotNull ProjectFromSourcesBuilder builder) {
    final List<ModuleDescriptor> modules = new ArrayList<>();
    for (DetectedProjectRoot root : roots) {
      modules.add(new ModuleDescriptor(root.getDirectory(), FlutterModuleUtils.getFlutterModuleType(), Collections.emptyList()));
    }

    projectDescriptor.setModules(modules);
    builder.setupModulesByContentRoots(projectDescriptor, roots);
  }

  private static class FlutterProjectRoot extends DetectedProjectRoot {
    public FlutterProjectRoot(@NotNull File directory) {
      super(directory);
    }

    @NotNull
    @Override
    public String getRootTypeName() {
      return "Flutter";
    }
  }

  private static class DartProjectRoot extends DetectedProjectRoot {
    public DartProjectRoot(@NotNull File directory) {
      super(directory);
    }

    @NotNull
    @Override
    public String getRootTypeName() {
      return "Dart";
    }
  }
}
