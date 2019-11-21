/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.project.FlutterProjectCreator;
import io.flutter.project.FlutterProjectModel;
import io.flutter.utils.AndroidUtils;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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

      // Ensure Gradle sync runs to link in the new add-to-app module.
      AndroidUtils.scheduleGradleSync(hostProject);
      // TODO(messick) Generate run configs for release and debug. (If needed.)
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
            settingsWriter.append(new String(settingsFile.contentsToByteArray(false), StandardCharsets.UTF_8));
            settingsWriter.append(System.lineSeparator());
          }
          settingsWriter.append("// Generated file. Do not edit.");
          settingsWriter.append(System.lineSeparator());
          settingsWriter.append("include ':.android'");
          settingsWriter.append(System.lineSeparator());
          settingsFile.setBinaryContent(settingsWriter.toString().getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException e) {
          // Should not happen
        }
      });
    }
  }
}
