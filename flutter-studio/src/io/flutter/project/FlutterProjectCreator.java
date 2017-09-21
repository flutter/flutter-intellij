/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.android.repository.io.FileOpUtils;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.ProjectViewPane;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.ProjectWizardUtil;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
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
import io.flutter.module.FlutterModuleBuilder;
import io.flutter.module.FlutterSmallIDEProjectGenerator;
import io.flutter.sdk.FlutterCreateAdditionalSettings;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.EnumSet;

/**
 * Create a Flutter project.
 *
 * There are three types of Flutter projects: applications, plugins, and packages. Each can be represented two different
 * ways in IntelliJ. A Flutter project can be:
 * <ul>
 * <li>a module in an IntelliJ project. @see #createModule()</li>
 * <li>a top-level IntelliJ project. @see @createProject()</li>
 * </ul>
 */
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

  public void createModule() {
    Project project = myModel.project().getValue();
    VirtualFile baseDir = project.getBaseDir();
    if (baseDir == null) {
      return; // Project was deleted.
    }
    String baseDirPath = baseDir.getPath();
    String moduleName = ProjectWizardUtil.findNonExistingFileName(baseDirPath, myModel.projectName().get(), "");
    String contentRoot = baseDirPath + "/" + moduleName;
    File location = new File(contentRoot);
    if (!location.exists() && !location.mkdirs()) {
      String message = ActionsBundle.message("action.NewDirectoryProject.cannot.create.dir", location.getAbsolutePath());
      Messages.showErrorDialog(project, message, ActionsBundle.message("action.NewDirectoryProject.title"));
      return;
    }

    // ModuleBuilder mixes UI and model code too much to easily reuse. We have to override a bunch of stuff to ensure
    // that methods which expect a live UI do not get called.
    FlutterModuleBuilder builder = new FlutterModuleBuilder() {
      @NotNull
      @Override
      public FlutterCreateAdditionalSettings getAdditionalSettings() {
        return makeAdditionalSettings();
      }

      @Override
      protected FlutterSdk getFlutterSdk() {
        return FlutterSdk.forPath(myModel.flutterSdk().get());
      }

      @Override
      public boolean validate(Project current, Project dest) {
        return true;
      }

      @Override
      public ModuleWizardStep getCustomOptionsStep(final WizardContext context, final Disposable parentDisposable) {
        return null;
      }

      @Override
      public void setFlutterSdkPath(String s) {
      }

      @Override
      public ModuleWizardStep modifySettingsStep(@NotNull SettingsStep settingsStep) {
        return null;
      }
    };
    builder.setName(myModel.projectName().get());
    builder.setModuleFilePath(FileUtil.toSystemIndependentName(contentRoot) + "/" + moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION);
    builder.commitModule(project, null);
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

    ProjectOpenedCallback callback =
      (project, module) -> ProgressManager.getInstance().run(new Task.Modal(null, "Creating Flutter Project", false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          indicator.setIndeterminate(true);

          FlutterSmallIDEProjectGenerator
            .generateProject(project, baseDir, myModel.flutterSdk().get(), module, makeAdditionalSettings());
        }
      });

    EnumSet<PlatformProjectOpenProcessor.Option> options = EnumSet.noneOf(PlatformProjectOpenProcessor.Option.class);
    Project newProject = PlatformProjectOpenProcessor.doOpenProject(baseDir, projectToClose, -1, callback, options);
    StartupManager.getInstance(newProject).registerPostStartupActivity(() -> ApplicationManager.getApplication().invokeLater(() -> {
      // We want to show the Project view, not the Android view since it doesn't make the Dart code visible.
      ProjectView.getInstance(newProject).changeView(ProjectViewPane.ID);
    }, ModalityState.NON_MODAL));
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
