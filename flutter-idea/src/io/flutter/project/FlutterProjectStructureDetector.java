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
import com.intellij.openapi.module.Module;
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
    String name = builder.getContext().getProjectName();
    if (name != null) {
      scheduleAndroidModuleAddition(name, modules, 0);
    }
  }

  private void scheduleAndroidModuleAddition(@NotNull String projectName, @NotNull List<ModuleDescriptor> modules, int tries) {
    //noinspection ConstantConditions
    final MessageBusConnection[] connection = {ApplicationManager.getApplication().getMessageBus().connect()};
    scheduleDisconnectIfCancelled(connection);
    //noinspection ConstantConditions
    connection[0].subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      //See https://plugins.jetbrains.com/docs/intellij/plugin-components.html#comintellijpoststartupactivity
      // for notice and documentation on the deprecation intentions of
      // Components from JetBrains.
      //
      // Migration forward has different directions before and after
      // 2023.1, if we can, it would be prudent to wait until we are
      // only supporting this major platform as a minimum version.
      //
      // https://github.com/flutter/flutter-intellij/issues/6953
      @Override
      public void projectOpened(@NotNull Project project) {
        if (connection[0] != null) {
          connection[0].disconnect();
          connection[0] = null;
        }
        if (!projectName.equals(project.getName())) {
          // This can happen if you have selected project roots in the import wizard then cancel the import,
          // and then import a project with a different name, before the scheduled disconnect runs.
          return;
        }
        //noinspection ConstantConditions
        StartupManager.getInstance(project).runAfterOpened(() -> {
          DumbService.getInstance(project).smartInvokeLater(() -> {
            for (ModuleDescriptor module : modules) {
              assert module != null;
              Set<File> roots = module.getContentRoots();
              String moduleName = module.getName();
              if (roots == null || roots.size() != 1 || moduleName == null) continue;
              File root = roots.iterator().next();
              assert root != null;
              if (!projectHasContentRoot(project, root)) continue;
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

  @SuppressWarnings("ConstantConditions")
  private static void scheduleDisconnectIfCancelled(MessageBusConnection[] connection) {
    // If the import was cancelled the subscription will never be removed.
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      Project project = ProjectManager.getInstance().getDefaultProject();
      try {
        Thread.sleep(300000L); // Allow five minutes to complete the project import wizard.
      }
      catch (InterruptedException ignored) {
      }
      if (connection[0] != null) {
        connection[0].disconnect();
        connection[0] = null;
      }
    });
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
