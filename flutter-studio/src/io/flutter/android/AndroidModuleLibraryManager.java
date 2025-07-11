/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.android;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.intellij.facet.FacetManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.IdeaProjectModelModifier;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.*;
import com.intellij.serviceContainer.ComponentManagerImpl;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.modules.CircularModuleDependenciesDetector;
import io.flutter.sdk.AbstractLibraryManager;
import io.flutter.sdk.FlutterSdkUtil;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.FlutterModuleUtils;
import io.flutter.utils.OpenApiUtils;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.android.tools.idea.gradle.util.GradleProjectSystemUtil.GRADLE_SYSTEM_ID;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED;
import static io.flutter.android.AndroidModuleLibraryType.LIBRARY_KIND;
import static io.flutter.android.AndroidModuleLibraryType.LIBRARY_NAME;

/**
 * Manages the Android libraries. Add the libraries used by Android modules referenced in a project
 * into the Flutter project, so full editing support is available. Add dependencies to each library
 * to the Android modules. Add a dependency from the Android module to the Flutter module so the
 * libraries can be resolved. Do not add a dependency from the Flutter module to the libraries since
 * Java and Kotlin code are only found in the Android modules. Also set the project SDK to that used
 * by Android.
 * <p>
 * TODO(messick) Test with plugins and modules
 * These are not looking so good. Source files are not marked correctly. Re-check make-host-app-editable.
 *
 * @see AndroidModuleLibraryType
 * @see AndroidModuleLibraryProperties
 */
public class AndroidModuleLibraryManager extends AbstractLibraryManager<AndroidModuleLibraryProperties> {
  private static final String BUILD_FILE_NAME = "build.gradle";
  private final AtomicBoolean isUpdating = new AtomicBoolean(false);
  private final AtomicBoolean isDisabled = new AtomicBoolean(false);

  public AndroidModuleLibraryManager(@NotNull Project project) {
    super(project);
  }

  public void update() {
    doGradleSync(getProject(), androidProject -> androidProject != null ? scheduleAddAndroidLibraryDeps(androidProject) : null);
  }

  private Void scheduleAddAndroidLibraryDeps(@NotNull Project androidProject) {
    OpenApiUtils.safeInvokeLater(
      () -> addAndroidLibraryDependencies(androidProject),
      ModalityState.nonModal());
    return null;
  }

  private void addAndroidLibraryDependencies(@NotNull Project androidProject) {
    for (Module flutterModule : FlutterModuleUtils.getModules(getProject())) {
      if (FlutterModuleUtils.isFlutterModule(flutterModule)) {
        for (Module module : OpenApiUtils.getModules(androidProject)) {
          addAndroidLibraryDependencies(androidProject, module, flutterModule);
        }
      }
    }
    isUpdating.set(false);
  }

  private void addAndroidLibraryDependencies(@NotNull Project androidProject,
                                             @NotNull Module androidModule,
                                             @NotNull Module flutterModule) {
    //AndroidSdkUtils.setupAndroidPlatformIfNecessary(androidModule, true);
    Sdk currentSdk = ModuleRootManager.getInstance(androidModule).getSdk();
    if (currentSdk != null) {
      // TODO(messick) Add sdk dependency on currentSdk if not already set
    }
    LibraryTable androidProjectLibraryTable = OpenApiUtils.getLibraryTable(androidProject);
    if (androidProjectLibraryTable == null) return;
    Library[] androidProjectLibraries = androidProjectLibraryTable.getLibraries();
    LibraryTable flutterProjectLibraryTable = OpenApiUtils.getLibraryTable(getProject());
    if (flutterProjectLibraryTable == null) return;
    Library[] flutterProjectLibraries = flutterProjectLibraryTable.getLibraries();
    Set<String> knownLibraryNames = new HashSet<>(flutterProjectLibraries.length);
    for (Library lib : flutterProjectLibraries) {
      if (lib.getName() != null) {
        knownLibraryNames.add(lib.getName());
      }
    }
    for (Library library : androidProjectLibraries) {
      if (library.getName() != null && !knownLibraryNames.contains(library.getName())) {

        List<String> roots = Arrays.asList(library.getRootProvider().getUrls(OrderRootType.CLASSES));
        Set<String> filteredRoots = roots.stream().filter(AndroidModuleLibraryManager::shouldIncludeRoot).collect(Collectors.toSet());
        if (filteredRoots.isEmpty()) continue;

        HashSet<String> sources = new HashSet<>(Arrays.asList(library.getRootProvider().getUrls(OrderRootType.SOURCES)));

        updateLibraryContent(library.getName(), filteredRoots, sources);
        updateAndroidModuleLibraryDependencies(flutterModule);
      }
    }
  }

  @Override
  protected void updateModuleLibraryDependencies(@NotNull Library library) {
    for (final Module module : OpenApiUtils.getModules(getProject())) {
      // The logic is inverted wrt superclass.
      if (!FlutterModuleUtils.declaresFlutter(module)) {
        addFlutterLibraryDependency(module, library);
      }
      else {
        removeFlutterLibraryDependency(module, library);
      }
    }
  }

  private void updateAndroidModuleLibraryDependencies(Module flutterModule) {
    for (final Module module : OpenApiUtils.getModules(getProject())) {
      if (module != flutterModule) {
        if (null != FacetManager.getInstance(module).findFacet(AndroidFacet.ID, "Android")) {
          Object circularModules = CircularModuleDependenciesDetector.addingDependencyFormsCircularity(module, flutterModule);
          if (circularModules == null) {
            ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
            if (!rootManager.isDependsOn(flutterModule)) {
              JavaProjectModelModifier[] modifiers = JavaProjectModelModifier.EP_NAME.getExtensions(getProject());
              for (JavaProjectModelModifier modifier : modifiers) {
                if (modifier instanceof IdeaProjectModelModifier) {
                  modifier.addModuleDependency(module, flutterModule, DependencyScope.COMPILE, false);
                }
              }
            }
          }
        }
      }
    }
  }

  @NotNull
  @Override
  protected String getLibraryName() {
    // This is not used since we create many libraries, not one.
    return LIBRARY_NAME;
  }

  @NotNull
  @Override
  protected PersistentLibraryKind<AndroidModuleLibraryProperties> getLibraryKind() {
    return LIBRARY_KIND;
  }

  private void scheduleUpdate() {
    if (isUpdating.get() || isDisabled.get()) {
      return;
    }

    final Runnable runnable = this::updateAndroidLibraries;
    DumbService.getInstance(getProject()).smartInvokeLater(runnable, ModalityState.nonModal());
  }

  private void updateAndroidLibraries() {
    if (!isUpdating.compareAndSet(false, true)) {
      return;
    }
    update();
  }

  private void doGradleSync(@NotNull Project flutterProject, @NotNull Function<Project, Void> callback) {
    // TODO(messick): Collect URLs for all Android modules, including those within plugins.
    final VirtualFile baseDir = ProjectUtil.guessProjectDir(flutterProject);
    if (baseDir == null) return;
    VirtualFile dir = baseDir.findChild("android");
    if (dir == null) dir = baseDir.findChild(".android"); // For modules.
    if (dir == null) return;
    EmbeddedAndroidProject androidProject = new EmbeddedAndroidProject(Paths.get(FileUtilRt.toSystemIndependentName(dir.getPath())));
    androidProject.init42(null);
    Disposer.register(flutterProject, androidProject);

    GradleSyncListener listener = new GradleSyncListener() {
      @Override
      public void syncSucceeded(@NotNull Project project) {
        if (isUpdating.get()) {
          callback.apply(androidProject);
        }
      }

      @Override
      public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
        isUpdating.set(false);
      }

      @Override
      public void syncSkipped(@NotNull Project project) {
        isUpdating.set(false);
      }
    };

    GradleSyncInvoker.Request request = new GradleSyncInvoker.Request(TRIGGER_PROJECT_MODIFIED);
    //request.runInBackground = true;
    GradleSyncInvoker gradleSyncInvoker = ApplicationManager.getApplication().getService(GradleSyncInvoker.class);
    gradleSyncInvoker.requestProjectSync(androidProject, request, listener);
  }

  private static boolean shouldIncludeRoot(String path) {
    return !path.endsWith("res") && !path.contains("flutter.jar") && !path.contains("flutter-x86.jar");
  }

  @NotNull
  public static AndroidModuleLibraryManager getInstance(@NotNull final Project project) {
    return Objects.requireNonNull(project.getService(AndroidModuleLibraryManager.class));
  }

  public static void startWatching(@NotNull Project project) {
    // Start a process to monitor changes to Android dependencies and update the library content.
    if (project.isDefault()) {
      return;
    }
    if (hasAndroidDir(project)) {
      AndroidModuleLibraryManager manager = getInstance(project);
      VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileContentsChangedAdapter() {
        @Override
        protected void onFileChange(@NotNull VirtualFile file) {
          fileChanged(project, file);
        }

        @Override
        protected void onBeforeFileChange(@NotNull VirtualFile file) {
        }
      }, project);

      project.getMessageBus().connect().subscribe(ModuleRootListener.TOPIC, new ModuleRootListener() {
        @Override
        public void rootsChanged(@NotNull ModuleRootEvent event) {
          manager.scheduleUpdate();
        }
      });
      manager.scheduleUpdate();
    }
  }

  private static boolean hasAndroidDir(@NotNull Project project) {
    if (FlutterSdkUtil.hasFlutterModules(project)) {
      VirtualFile base = ProjectUtil.guessProjectDir(project);
      VirtualFile dir = base.findChild("android");
      if (dir == null) dir = base.findChild(".android");
      return dir != null;
    }
    else {
      return false;
    }
  }

  private static void fileChanged(@NotNull final Project project, @NotNull final VirtualFile file) {
    if (!BUILD_FILE_NAME.equals(file.getName())) {
      return;
    }
    if (LocalFileSystem.getInstance() != file.getFileSystem() && !ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }
    final VirtualFile baseDir = ProjectUtil.guessProjectDir(project);
    if (baseDir == null) {
      return;
    }
    if (!VfsUtilCore.isAncestor(baseDir, file, true)) {
      return;
    }
    getInstance(project).scheduleUpdate();
  }

  private class EmbeddedAndroidProject extends ProjectImpl {
    private Path path;

    protected EmbeddedAndroidProject(@NotNull Path filePath) {
      super((ComponentManagerImpl)ApplicationManager.getApplication(), filePath, TEMPLATE_PROJECT_NAME);
      path = filePath;
    }

    static final String TEMPLATE_PROJECT_NAME = "_android";

    public @NotNull String getLocationHash() {
      return "";
    }

    public void init42(@Nullable ProgressIndicator indicator) {
      boolean finished = false;
      try {
        //ProjectManagerImpl.initProject(path, this, true, true, null, null);
        Method method = ReflectionUtil
          .getDeclaredMethod(ProjectManagerImpl.class, "initProject", Path.class, ProjectImpl.class, boolean.class, boolean.class,
                             Project.class, ProgressIndicator.class);
        if (method == null) {
          disableGradleSyncAndNotifyUser();
          return;
        }
        try {
          method.invoke(null, path, this, true, true, null, null);
        }
        catch (IllegalAccessException | InvocationTargetException e) {
          disableGradleSyncAndNotifyUser();
          return;
        }
        finished = true;
      }
      finally {
        if (!finished) {
          TransactionGuard.submitTransaction(this, () -> WriteAction.run(() -> {
            if (isDisposed() && !isDisabled.get()) {
              Disposer.dispose(this);
            }
          }));
        }
      }
    }

    private void disableGradleSyncAndNotifyUser() {
      final FlutterSettings instance = FlutterSettings.getInstance();
      instance.setSyncingAndroidLibraries(false);
      isDisabled.set(true);
      final Notification notification = new Notification(
        GRADLE_SYSTEM_ID.getReadableName() + " sync",
        "Gradle sync disabled",
        "An internal error prevents Gradle from analyzing the Android module at " + path,
        NotificationType.WARNING);

      Notifications.Bus.notify(notification, this);
    }
  }
}
