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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.pub.PubRoot;
import io.flutter.utils.FlutterModuleUtils;
import io.flutter.utils.OpenApiUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public class FlutterProjectStructureDetector extends ProjectStructureDetector {

  @NotNull
  @Override
  public DirectoryProcessingResult detectRoots(@NotNull File parent,
                                               @SuppressWarnings("NullableProblems") @NotNull File[] children,
                                               @NotNull File base,
                                               @NotNull List<DetectedProjectRoot> result) {
    final VirtualFile dir = VfsUtil.findFileByIoFile(parent, false);
    if (dir != null) {
      final PubRoot pubRoot = PubRoot.forDirectory(dir);
      if (pubRoot != null) {
        if (pubRoot.declaresFlutter()) {
          result.add(new FlutterProjectRoot(parent));
        }
        else {
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
      if (root != null) {
        //noinspection ConstantConditions
        modules.add(new ModuleDescriptor(root.getDirectory(), FlutterModuleUtils.getFlutterModuleType(), Collections.emptyList()));
      }
    }

    projectDescriptor.setModules(modules);
    builder.setupModulesByContentRoots(projectDescriptor, roots);
  }

  @SuppressWarnings("ConstantConditions")
  private static boolean projectHasContentRoot(@NotNull Project project, @NotNull File root) {
    // Verify that the project has the given content root. If the import was cancelled and restarted
    // for the same project, but a content root was not selected the second time, then it might be absent.
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(root);
    for (Module module : OpenApiUtils.getModules(project)) {
      for (VirtualFile file : OpenApiUtils.getContentRoots(module)) {
        if (file != null && file.equals(virtualFile)) {
          return true;
        }
      }
    }
    return false;
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
