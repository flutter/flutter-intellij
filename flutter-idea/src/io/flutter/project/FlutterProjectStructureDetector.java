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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import io.flutter.module.FlutterModuleBuilder;
import io.flutter.pub.PubRoot;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public class FlutterProjectStructureDetector extends ProjectStructureDetector {
  private static final Logger LOG = Logger.getInstance(ProjectStructureDetector.class);

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
    //noinspection ConstantConditions
    scheduleAndroidModuleAddition(builder.getContext().getProjectName(), modules, 0);
  }

  private void scheduleAndroidModuleAddition(@NotNull String projectName, @NotNull List<ModuleDescriptor> modules, int tries) {
    //noinspection ConstantConditions
    @NotNull MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        connection.disconnect();
        if (!projectName.equals(project.getName())) {
          LOG.info("Project name is " + project.getName() + " but was expecting " + projectName);
        }
        //noinspection ConstantConditions
        StartupManager.getInstance(project).runAfterOpened(() -> {
          //noinspection ConstantConditions
          DumbService.getInstance(project).smartInvokeLater(() -> {
            for (ModuleDescriptor module : modules) {
              assert module != null;
              Set<File> roots = module.getContentRoots();
              String moduleName = module.getName();
              if (roots == null || roots.size() != 1 || moduleName == null) continue;
              File root = roots.iterator().next();
              String imlName = moduleName + "_android.iml";
              File moduleDir = null;
              if (new File(root, imlName).exists()) {
                moduleDir = root;
              }
              else {
                for (String name : new String[]{"android", ".android"}) {
                  File dir = new File(root, name);
                  if (dir.exists() && new File(dir, imlName).exists()) {
                    moduleDir = dir;
                    break;
                  }
                }
              }
              if (moduleDir == null) continue;
              try {
                // Searching for a module by name and skipping the next line if found,
                // will not always eliminate the exception caught here.
                // Specifically, if the project had previously been opened and the caches were not cleared.
                //noinspection ConstantConditions
                FlutterModuleBuilder.addAndroidModule(project, null, moduleDir.getPath(), module.getName(), true);
              }
              catch (IllegalStateException ignored) {
              }
              // Check for a plugin example module.
              File example = new File(root, "example");
              if (example.exists()) {
                File android = new File(example, "android");
                File exampleFile;
                if (android.exists() && (exampleFile = new File(android, moduleName + "_example_android.iml")).exists()) {
                  try {
                    //noinspection ConstantConditions
                    FlutterModuleBuilder.addAndroidModuleFromFile(project, null,
                                                                  LocalFileSystem.getInstance().findFileByIoFile(exampleFile));
                  }
                  catch (IllegalStateException ignored) {
                  }
                }
              }
            }
          });
        });
      }
    });
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
