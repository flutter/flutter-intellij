/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.android.repository.io.FileOpUtils;
import com.intellij.conversion.ConversionListener;
import com.intellij.conversion.ConversionService;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.impl.ProjectUtil;
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
import io.flutter.FlutterUtils;
import io.flutter.module.FlutterModuleBuilder;
import io.flutter.module.FlutterProjectType;
import io.flutter.module.FlutterSmallIDEProjectGenerator;
import io.flutter.sdk.FlutterCreateAdditionalSettings;
import io.flutter.sdk.FlutterSdk;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetType;
import org.jetbrains.android.facet.IdeaSourceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

import java.io.File;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import static com.intellij.openapi.util.io.FileUtilRt.getRelativePath;
import static com.intellij.openapi.util.io.FileUtilRt.toSystemIndependentName;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

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
  private static final Logger LOG = Logger.getInstance(FlutterProjectCreator.class);
  private static final String SEPARATOR = "/";
  @NotNull private final FlutterProjectModel myModel;

  public FlutterProjectCreator(@NotNull FlutterProjectModel model) {
    myModel = model;
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

  private static void deleteDirectoryContents(File location) {
    File[] files = location.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isDirectory()) {
          deleteDirectoryContents(file);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
      }
    }
  }

  private static void configureFacet(@NotNull AndroidFacet facet, @NotNull File location, @NotNull String dirName) {
    JpsAndroidModuleProperties facetProperties = facet.getProperties();

    File modulePath = new File(location, dirName);
    IdeaSourceProvider sourceProvider = IdeaSourceProvider.create(facet);
    facetProperties.MANIFEST_FILE_RELATIVE_PATH = relativePath(modulePath, sourceProvider.getManifestFile());
    facetProperties.RES_FOLDER_RELATIVE_PATH = relativePath(modulePath, sourceProvider.getResDirectories());
    facetProperties.ASSETS_FOLDER_RELATIVE_PATH = relativePath(modulePath, sourceProvider.getAssetsDirectories());
    facetProperties.ALLOW_USER_CONFIGURATION = false;
  }

  @NotNull
  private static String relativePath(@NotNull File basePath, @NotNull Collection<VirtualFile> dirs) {
    return relativePath(basePath, getFirstItem(dirs));
  }

  @NotNull
  private static String relativePath(@NotNull File basePath, @Nullable VirtualFile file) {
    String relativePath = null;
    if (file != null) {
      relativePath = getRelativePath(basePath, new File(file.getPath()));
    }
    if (relativePath != null && !relativePath.startsWith(SEPARATOR)) {
      return SEPARATOR + toSystemIndependentName(relativePath);
    }
    return "";
  }

  private static String reversedOrgFromPackage(@NotNull String packageName) {
    int idx = packageName.lastIndexOf('.');
    if (idx <= 0) {
      return packageName;
    }
    return packageName.substring(0, idx);
  }

  private static void reloadProjectNow(Project project) {
    ApplicationManager.getApplication().invokeAndWait(() -> {
      String presentableUrl = project.getPresentableUrl();
      if (!ProjectUtil.closeAndDispose(project)) {
        return;
      }
      ProjectUtil.openProject(presentableUrl, null, true);
    });
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
    builder.setModuleFilePath(toSystemIndependentName(contentRoot) + "/" + moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION);
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
    final File baseFile = new File(location, myModel.projectName().get());
    //noinspection ResultOfMethodCallIgnored
    baseFile.mkdirs();
    final VirtualFile baseDir = ApplicationManager
      .getApplication().runWriteAction((Computable<VirtualFile>)() -> LocalFileSystem.getInstance().refreshAndFindFileByIoFile(baseFile));
    if (baseDir == null) {
      LOG.error("Couldn't find '" + location + "' in VFS");
      return;
    }
    VfsUtil.markDirtyAndRefresh(false, true, true, baseDir);

    RecentProjectsManager.getInstance().setLastProjectCreationLocation(location.getPath());

    ProjectOpenedCallback callback =
      (project, module) -> ProgressManager.getInstance().run(new Task.Modal(null, "Creating Flutter Project", false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          indicator.setIndeterminate(true);

          // The IDE has already created some files. Flutter won't overwrite them, but we want the versions provided by Flutter.
          deleteDirectoryContents(baseFile);

          // Add an Android facet if needed by the project type.
          if (myModel.projectType().getValue() != FlutterProjectType.PACKAGE) {
            ModifiableFacetModel model = FacetManager.getInstance(module).createModifiableModel();
            AndroidFacetType facetType = AndroidFacet.getFacetType();
            AndroidFacet facet = facetType.createFacet(module, AndroidFacet.NAME, facetType.createDefaultConfiguration(), null);
            model.addFacet(facet);
            configureFacet(facet, baseFile, "android");
            File appLocation = new File(baseFile, "android");
            //noinspection ResultOfMethodCallIgnored
            appLocation.mkdirs();
            AndroidFacet appFacet = facetType.createFacet(module, AndroidFacet.NAME, facetType.createDefaultConfiguration(), null);
            model.addFacet(appFacet);
            configureFacet(appFacet, appLocation, "app");
          }

          FlutterSmallIDEProjectGenerator
            .generateProject(project, baseDir, myModel.flutterSdk().get(), module, makeAdditionalSettings());

          // Reload the project to use the configuration written by Flutter.
          VfsUtil.markDirtyAndRefresh(false, true, true, baseDir);
          ConversionService.getInstance().convertSilently(baseDir.getPath(), new MyConversionListener());
          VfsUtil.markDirtyAndRefresh(false, true, true, baseDir);
          reloadProjectNow(project);
        }
      });

    EnumSet<PlatformProjectOpenProcessor.Option> options = EnumSet.noneOf(PlatformProjectOpenProcessor.Option.class);
    PlatformProjectOpenProcessor.doOpenProject(baseDir, projectToClose, -1, callback, options);
    // The project was reloaded by the callback. Find the new Project object.
    Project newProject = FlutterUtils.findProject(baseDir.getPath());
    if (newProject != null) {
      StartupManager.getInstance(newProject).registerPostStartupActivity(() -> ApplicationManager.getApplication().invokeLater(() -> {
        disableUserConfig(newProject);
        // We want to show the Project view, not the Android view since it doesn't make the Dart code visible.
        ProjectView.getInstance(newProject).changeView(ProjectViewPane.ID);
        //ProjectManager.getInstance().reloadProject(newProject);
      }, ModalityState.NON_MODAL));
    }
  }

  private FlutterCreateAdditionalSettings makeAdditionalSettings() {
    return new FlutterCreateAdditionalSettings.Builder()
      .setDescription(myModel.description().get().isEmpty() ? null : myModel.description().get())
      .setType(myModel.projectType().getValue())
      .setOrg(myModel.packageName().get().isEmpty() ? null : reversedOrgFromPackage(myModel.packageName().get()))
      .setKotlin(myModel.useKotlin().get() ? true : null)
      .setSwift(myModel.useSwift().get() ? true : null)
      .build();
  }

  public static class MyConversionListener implements ConversionListener {
    private boolean myConversionNeeded;
    private boolean myConverted;

    @Override
    public void conversionNeeded() {
      myConversionNeeded = true;
    }

    @Override
    public void successfullyConverted(File backupDir) {
      myConverted = true;
    }

    @Override
    public void error(String message) {
    }

    @Override
    public void cannotWriteToFiles(List<File> readonlyFiles) {
    }

    public boolean isConversionNeeded() {
      return myConversionNeeded;
    }

    public boolean isConverted() {
      return myConverted;
    }
  }
}
