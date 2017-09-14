/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.android.repository.io.FileOpUtils;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.projectImport.ProjectOpenedCallback;
import io.flutter.module.FlutterSmallIDEProjectGenerator;
import io.flutter.sdk.FlutterCreateAdditionalSettings;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.EnumSet;

public class FlutterProjectCreator {
  private static final Logger LOG = Logger.getInstance(FlutterProjectModel.class);

  @NotNull private final FlutterProjectModel myModel;

  public FlutterProjectCreator(@NotNull FlutterProjectModel model) {
    myModel = model;
  }

  public static boolean finalValidityCheckPassed(@NotNull String projectLocation) {
    // See AS NewProjectModel.ProjectTemplateRenderer.doDryRun() for why this is necessary.
    boolean couldEnsureLocationExists = WriteCommandAction.runWriteCommandAction(null, (Computable<Boolean>)() -> {
      try {
        if (VfsUtil.createDirectoryIfMissing(projectLocation) != null && FileOpUtils.create().canWrite(new File(projectLocation))) {
          return true;
        }
      }
      catch (Exception e) {
        LOG.error(String.format("Exception thrown when creating target project location: %1$s", projectLocation), e);
      }
      return false;
    });
    if (!couldEnsureLocationExists) {
      String msg =
        "Could not ensure the target project location exists and is accessible:\n\n%1$s\n\nPlease try to specify another path.";
      Messages.showErrorDialog(String.format(msg, projectLocation), "Error Creating Project");
      return false;
    }
    return true;
  }

  public void createProject() {
    IdeFrame frame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
    final Project projectToClose = frame != null ? frame.getProject() : null;

    final File location = new File(FileUtil.toSystemDependentName(myModel.projectLocation().get()));
    if (!location.exists() && !location.mkdirs()) {
      String message = ActionsBundle.message("action.NewDirectoryProject.cannot.create.dir", location.getAbsolutePath());
      Messages.showErrorDialog(projectToClose, message, ActionsBundle.message("action.NewDirectoryProject.title"));
      return;
    }
    final VirtualFile baseDir = ApplicationManager
      .getApplication().runWriteAction((Computable<VirtualFile>)() -> LocalFileSystem.getInstance().refreshAndFindFileByIoFile(location));
    if (baseDir == null) {
      LOG.error("Couldn't find '" + location + "' in VFS");
      return;
    }
    VfsUtil.markDirtyAndRefresh(false, true, true, baseDir);

    RecentProjectsManager.getInstance().setLastProjectCreationLocation(location.getParent());

    ProjectOpenedCallback callback = (project, module) -> {
      FlutterSmallIDEProjectGenerator
        .generateProject(project, baseDir, myModel.flutterSdk().get(), module, makeAdditionalSettings());
    };

    EnumSet<PlatformProjectOpenProcessor.Option> options = EnumSet.noneOf(PlatformProjectOpenProcessor.Option.class);
    Project newProject = PlatformProjectOpenProcessor.doOpenProject(baseDir, projectToClose, -1, callback, options);
    StartupManager.getInstance(newProject).registerPostStartupActivity(() -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        // We want to show the Project view, not the Android view since it doesn't make the Dart code visible.
        ProjectView.getInstance(newProject).changeView(ProjectViewPane.ID);
      }, ModalityState.NON_MODAL);
    });
  }

  private FlutterCreateAdditionalSettings makeAdditionalSettings() {
    return new FlutterCreateAdditionalSettings.Builder()
      .setDescription(myModel.description().get().isEmpty() ? null : myModel.description().get())
      .setType(myModel.projectType().getValue())
      .setOrg(myModel.companyDomain().get().isEmpty() ? null : myModel.companyDomain().get())
      .setKotlin(myModel.useKotlin().get() ? true : null)
      .setSwift(myModel.useSwift().get() ? true : null)
      .build();
  }
}
