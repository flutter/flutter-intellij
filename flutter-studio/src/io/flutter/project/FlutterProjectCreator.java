/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import static com.intellij.openapi.util.io.FileUtilRt.toSystemIndependentName;
import static io.flutter.FlutterUtils.disableGradleProjectMigrationNotification;

import com.android.repository.io.FileOpUtils;
import com.intellij.conversion.ConversionListener;
import com.intellij.execution.OutputListener;
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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectTypeService;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.platform.PlatformProjectOpenProcessor;
import io.flutter.FlutterMessages;
import io.flutter.FlutterUtils;
import io.flutter.ProjectOpenActivity;
import io.flutter.module.FlutterModuleBuilder;
import io.flutter.pub.PubRoot;
import io.flutter.sdk.FlutterCreateAdditionalSettings;
import io.flutter.sdk.FlutterSdk;
import io.flutter.utils.AndroidUtils;
import io.flutter.utils.FlutterModuleUtils;
import java.io.File;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Create a Flutter project.
 * <p>
 * There are four types of Flutter projects: applications, plugins, modules, and packages. Each
 * can be represented two different ways in IntelliJ. A Flutter project can be:
 * <ul>
 * <li>a module in an IntelliJ project. @see #createModule()</li>
 * <li>a top-level IntelliJ project. @see @createProject()</li>
 * </ul>
 */
public class FlutterProjectCreator {
  private static final Logger LOG = Logger.getInstance(FlutterProjectCreator.class);
  private static final String SEPARATOR = "/";
  @NotNull private final FlutterProjectModel myModel;

  public FlutterProjectCreator(@NotNull FlutterProjectModel model) {
    myModel = model;
  }

  public void createModule() {
    Project project = myModel.project().getValue();
    VirtualFile baseDir = project.getBaseDir();
    if (baseDir == null) {
      return; // Project was deleted.
    }
    String moduleName, contentRoot;
    if (AndroidUtils.isAndroidProject(project)) {
      VirtualFile location = getLocationFromModel(null, false);
      if (location == null) {
        return;
      }
      moduleName = myModel.projectName().get();
      contentRoot = location.getPath();
    }
    else {
      String baseDirPath = baseDir.getPath();
      moduleName = ProjectWizardUtil.findNonExistingFileName(baseDirPath, myModel.projectName().get(), "");
      contentRoot = baseDirPath + "/" + moduleName;
    }
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
    builder.setModuleFilePath(toSystemIndependentName(contentRoot) + "/" + moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION);
    // TODO(messick): Refactor commitModule(). We're getting "Already disposed: Module" errors when a Flutter module is added
    // to an Android app (as a module using the new module wizard).
    builder.commitModule(project, null);
  }

  public void createProject() {
    IdeFrame frame = IdeFocusManager.getGlobalInstance().getLastFocusedFrame();
    final Project projectToClose = frame != null ? frame.getProject() : null;

    VirtualFile baseDir = getLocationFromModel(projectToClose, true);
    if (baseDir == null) {
      return;
    }
    //noinspection ConstantConditions (Keep this refresh even if the converter is removed.)
    VfsUtil.markDirtyAndRefresh(false, true, true, baseDir);

    // Create the project files using 'flutter create'.
    FlutterSdk sdk = FlutterSdk.forPath(myModel.flutterSdk().get());
    if (sdk == null) {
      FlutterMessages.showError("Error creating project", myModel.flutterSdk().get() + " is not a valid Flutter SDK");
      return;
    }
    final OutputListener listener = new OutputListener();
    // TODO(messick,pq): Refactor createFiles() to accept a logging interface instead of module, and display it in the wizard.
    ProgressManager progress = ProgressManager.getInstance();
    AtomicReference<PubRoot> result = new AtomicReference<>(null);
    progress.runProcessWithProgressSynchronously(() -> {
      progress.getProgressIndicator().setIndeterminate(true);
      sdk.createFiles(baseDir, null, listener, makeAdditionalSettings());
      VfsUtil.markDirtyAndRefresh(false, true, true, baseDir);
      result.set(PubRoot.forDirectory(baseDir));
    }, "Creating Flutter Project", false, null);
    PubRoot root = result.get();
    if (root == null) {
      String stderr = listener.getOutput().getStderr();
      FlutterMessages.showError("Error creating project", stderr.isEmpty() ? "Flutter create command was unsuccessful" : stderr);
      return;
    }

    Project project = null;
    if (myModel.shouldOpenNewWindow()) {
      // Open the project window.
      EnumSet<PlatformProjectOpenProcessor.Option> options = EnumSet.noneOf(PlatformProjectOpenProcessor.Option.class);
      project = PlatformProjectOpenProcessor.doOpenProject(baseDir, projectToClose, -1, null, options);
    }

    if (project != null) {
      // Android Studio changes the default project type, so we need to set it.
      ProjectTypeService.setProjectType(project, ProjectOpenActivity.FLUTTER_PROJECT_TYPE);
      disableGradleProjectMigrationNotification(project);
      disableUserConfig(project);
      Project proj = project;
      StartupManager.getInstance(project).registerPostStartupActivity(
        () -> ApplicationManager.getApplication().invokeLater(
          () -> {
            // We want to show the Project view, not the Android view since it doesn't make the Dart code visible.
            DumbService.getInstance(proj).runWhenSmart(
              () -> {
                ToolWindowManager.getInstance(proj).getToolWindow(ToolWindowId.PROJECT_VIEW).activate(null);
                ProjectView.getInstance(proj).changeView(ProjectViewPane.ID);
              });
          }, ModalityState.defaultModalityState()));
    }
  }

  private VirtualFile getLocationFromModel(@Nullable Project projectToClose, boolean saveLocation) {
    final File location = new File(FileUtil.toSystemDependentName(myModel.projectLocation().get()));
    if (!location.exists() && !location.mkdirs()) {
      String message = ActionsBundle.message("action.NewDirectoryProject.cannot.create.dir", location.getAbsolutePath());
      Messages.showErrorDialog(projectToClose, message, ActionsBundle.message("action.NewDirectoryProject.title"));
      return null;
    }
    final File baseFile = new File(location, myModel.projectName().get());
    //noinspection ResultOfMethodCallIgnored
    baseFile.mkdirs();
    final VirtualFile baseDir = ApplicationManager.getApplication().runWriteAction(
      (Computable<VirtualFile>)() -> LocalFileSystem.getInstance().refreshAndFindFileByIoFile(baseFile));
    if (baseDir == null) {
      FlutterUtils.warn(LOG, "Couldn't find '" + location + "' in VFS");
      return null;
    }
    if (saveLocation) {
      RecentProjectsManager.getInstance().setLastProjectCreationLocation(location.getPath());
    }
    return baseDir;
  }

  private FlutterCreateAdditionalSettings makeAdditionalSettings() {
    return new FlutterCreateAdditionalSettings.Builder()
      .setDescription(myModel.description().get().isEmpty() ? null : myModel.description().get())
      .setType(myModel.projectType().getValue())
      .setOrg(myModel.packageName().get().isEmpty() ? null : reversedOrgFromPackage(myModel.packageName().get()))
      .setKotlin(isNotModule() && myModel.useKotlin().get() ? true : null)
      .setSwift(isNotModule() && myModel.useSwift().get() ? true : null)
      .setOffline(myModel.isOfflineSelected().get())
      .build();
  }

  private boolean isNotModule() {
    return !myModel.isModule();
  }

  public static void disableUserConfig(Project project) {
    if (FlutterModuleUtils.declaresFlutter(project)) {
      for (Module module : ModuleManager.getInstance(project).getModules()) {
        final AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet == null) {
          continue;
        }
        facet.getProperties().ALLOW_USER_CONFIGURATION = false;
      }
    }
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
        LOG.warn(String.format("Exception thrown when creating target project location: %1$s", projectLocation), e);
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

  @Nullable
  public static String reversedOrgFromPackage(@NotNull String packageName) {
    if (packageName.isEmpty()) {
      return null;
    }
    int idx = packageName.lastIndexOf('.');
    if (idx <= 0) {
      return packageName;
    }
    return packageName.substring(0, idx);
  }

  public static class MyConversionListener implements ConversionListener {
    private boolean myConversionNeeded;
    private boolean myConverted;

    @Override
    public void conversionNeeded() {
      myConversionNeeded = true;
    }

    @Override
    public void successfullyConverted(@NotNull File backupDir) {
      myConverted = true;
    }

    @Override
    public void error(@NotNull String message) {
    }

    //@Override
    @SuppressWarnings("override")
    public void cannotWriteToFiles(@NotNull List<? extends File> readonlyFiles) {
    }

    public boolean isConversionNeeded() {
      return myConversionNeeded;
    }

    public boolean isConverted() {
      return myConverted;
    }
  }
}
