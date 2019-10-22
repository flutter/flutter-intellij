/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.android.tools.idea.gradle.parser.BuildFileKey;
import com.android.tools.idea.gradle.parser.Dependency;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.gradle.parser.GradleSettingsFile;
import com.android.tools.idea.gradle.parser.Repository;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterUtils;
import io.flutter.actions.FlutterBuildActionGroup;
import io.flutter.project.FlutterProjectCreator;
import io.flutter.project.FlutterProjectModel;
import io.flutter.pub.PubRoot;
import io.flutter.sdk.FlutterSdk;
import io.flutter.utils.AndroidUtils;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.NotNull;

public class FlutterModuleModel extends FlutterProjectModel {
  public FlutterModuleModel(@NotNull FlutterProjectType type) {
    super(type);
  }

  @Override
  public boolean shouldOpenNewWindow() {
    return !isModule();
  }

  @Override
  protected void handleFinished() {
    // Do not call the superclass method.
    if (isModule()) {
      // The FlutterProjectType.MODULE is used in two places. In FlutterProjectModel it is used to create a new top-level
      // Flutter project. Here, it is used to create an Android module for add-to-app. In both cases the Flutter tool creates
      // a new project. In this case, we go on to link that project into an Android project as a new module.
      useAndroidX().set(true);
      // The host project is an Android app. This module should be created in its root directory.
      // Android Studio supports a colon-separated convention to specify sub-directories, which is not yet supported here.
      Project hostProject = project().getValue();
      String hostPath = hostProject.getBasePath();
      if (hostPath == null) {
        throw new InvalidDataException(); // Can't happen
      }
      projectLocation().set(hostPath);
      // Create the Flutter module as if it were a normal project that just happens to be located in an Android project directory.
      new FlutterProjectCreator(this).createModule();
      // Import the Flutter module into the Android project as if it had been created without any Android dependencies.
      new FlutterModuleImporter(this).importModule();
      // Link the new module into the Gradle project, which also enables co-editing.
      new FlutterGradleLinker(this).linkNewModule();
    }
    else {
      if (projectType().get().isPresent() && projectType().get().get() == FlutterProjectType.IMPORT) {
        String location = projectLocation().get();
        assert (!location.isEmpty());
        new FlutterModuleImporter(this).importModule();
      }
      else {
        // This branch should not be reached.
        assert (!projectName().get().isEmpty());
        assert (!flutterSdk().get().isEmpty());
        new FlutterProjectCreator(this).createModule();
      }
    }
  }

  private static class FlutterGradleLinker {
    @NotNull
    private final FlutterProjectModel model;

    private FlutterGradleLinker(@NotNull FlutterProjectModel model) {
      this.model = model;
    }

    private void linkNewModule() {
      Project hostProject = model.project().getValue();
      String hostPath = model.projectLocation().get();
      // If the module is not a direct child of the project root then this File() instance needs to be changed.
      // TODO(messick) Extend the new module wizard to allow nested directories, as Android Studio does using Gradle syntax.
      File flutterProject = new File(hostPath, model.projectName().get());
      VirtualFile flutterModuleDir = VfsUtil.findFileByIoFile(flutterProject, true);
      if (flutterModuleDir == null) {
        return; // Module creation failed; it was reported elsewhere.
      }
      addGradleToModule(flutterModuleDir);

      // Build the AAR repository, needed by Gradle linkage.
      PubRoot pubRoot = PubRoot.forDirectory(VfsUtil.findFileByIoFile(flutterProject, true));
      FlutterSdk sdk = FlutterSdk.forPath(model.flutterSdk().get());
      if (sdk == null) {
        return; // The error would have been shown in super.handleFinished().
      }
      OSProcessHandler handler =
        FlutterBuildActionGroup.build(hostProject, pubRoot, sdk, FlutterBuildActionGroup.BuildType.AAR, "Building AAR");
      handler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(@NotNull ProcessEvent event) {

          new ModuleCoEditHelper(hostProject, model.packageName().get(), flutterModuleDir).enable();

          // Ensure Gradle sync runs to link in the new add-to-app module.
          AndroidUtils.scheduleGradleSync(hostProject);
          // TODO(messick) Generate run configs for release and debug. (If needed.)
        }
      });
    }

    private static void addGradleToModule(VirtualFile moduleDir) {
      // Add settings.gradle to the Flutter module.
      ApplicationManager.getApplication().runWriteAction(() -> {
        try (StringWriter settingsWriter = new StringWriter()) {
          VirtualFile settingsFile = moduleDir.findOrCreateChildData(moduleDir, "settings.gradle");
          VirtualFile moduleFile = moduleDir.findChild(moduleDir.getName() + "_android.iml");
          if (moduleFile != null && moduleFile.exists()) {
            moduleFile.delete(moduleDir);
          }
          if (settingsFile.exists()) {
            // The default module template does not have a settings.gradle file so this should not happen.
            settingsWriter.append(new String(settingsFile.contentsToByteArray(false), Charset.defaultCharset()));
            settingsWriter.append(System.lineSeparator());
          }
          settingsWriter.append("// Generated file. Do not edit.");
          settingsWriter.append(System.lineSeparator());
          settingsWriter.append("include ':.android'");
          settingsWriter.append(System.lineSeparator());
          settingsFile.setBinaryContent(settingsWriter.toString().getBytes(Charset.defaultCharset()));
        }
        catch (IOException e) {
          // Should not happen
        }
      });
    }
  }

  // Edit settings.gradle according to add-to-app docs.
  private static class ModuleCoEditHelper {
    @NotNull
    private final Project project;
    @NotNull
    private final String packageName;
    @NotNull
    private final String projectName;
    @NotNull
    private final String pathToModule;

    private ModuleCoEditHelper(@NotNull Project project, @NotNull String packageName, @NotNull VirtualFile flutterModuleDir) {
      this.project = project;
      this.packageName = packageName;
      this.projectName = flutterModuleDir.getName();
      File flutterModuleRoot = new File(flutterModuleDir.getPath());
      VirtualFile projectRoot = FlutterUtils.getProjectRoot(project);
      this.pathToModule = Objects.requireNonNull(FileUtilRt.getRelativePath(new File(projectRoot.getPath(), "app"), flutterModuleRoot));
    }

    private void enable() {
      GradleSettingsFile settingsFile = GradleSettingsFile.get(project);
      if (settingsFile == null) {
        return;
      }
      GradleBuildFile gradleBuildFile = settingsFile.getModuleBuildFile(":app");
      if (gradleBuildFile == null) {
        return;
      }

      AtomicReference<List<Repository>> repositories = new AtomicReference<>();
      ApplicationManager.getApplication().runReadAction(() -> {
        //noinspection unchecked
        List<Repository> repBlock = (List<Repository>)gradleBuildFile.getValue(BuildFileKey.LIBRARY_REPOSITORY);
        repositories.set(repBlock != null ? repBlock : new ArrayList<>());
      });
      repositories.get().add(new Repository(Repository.Type.URL, pathToModule + "/build/host/outputs/repo"));

      AtomicReference<List<Dependency>> dependencies = new AtomicReference<>();
      ApplicationManager.getApplication().runReadAction(() -> {
        //noinspection unchecked
        List<Dependency> depBlock = (List<Dependency>)gradleBuildFile.getValue(BuildFileKey.DEPENDENCIES);
        dependencies.set(depBlock != null ? depBlock : new ArrayList<>());
      });
      Dependency.Scope scope = Dependency.Scope.getDefaultScope(project);
      String fqn = packagePrefix(packageName) + "." + projectName;
      dependencies.get().add(new Dependency(scope, Dependency.Type.EXTERNAL, fqn + ":flutter_release:1.0@aar"));

      WriteCommandAction.writeCommandAction(project).run(() -> {
        gradleBuildFile.removeValue(null, BuildFileKey.DEPENDENCIES);
        gradleBuildFile.setValue(BuildFileKey.LIBRARY_REPOSITORY, repositories.get());
        gradleBuildFile.setValue(BuildFileKey.DEPENDENCIES, dependencies.get());
      });
    }

    private static String packagePrefix(@NotNull String packageName) {
      int idx = packageName.lastIndexOf('.');
      if (idx <= 0) {
        return packageName;
      }
      return packageName.substring(0, idx);
    }
  }
}
