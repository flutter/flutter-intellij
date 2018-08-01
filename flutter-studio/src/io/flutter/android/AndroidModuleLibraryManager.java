/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.android;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.*;
import com.intellij.util.containers.hash.HashSet;
import io.flutter.sdk.AbstractLibraryManager;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static io.flutter.android.AndroidModuleLibraryType.LIBRARY_KIND;
import static io.flutter.android.AndroidModuleLibraryType.LIBRARY_NAME;

/**
 * Manages the Android Libraries library, which hooks the libraries used by Android modules referenced in a project
 * into the project, so full editing support is available.
 *
 * @see AndroidModuleLibraryType
 * @see AndroidModuleLibraryProperties
 */
public class AndroidModuleLibraryManager extends AbstractLibraryManager<AndroidModuleLibraryProperties> {
  private static final Logger LOG = Logger.getInstance(AndroidModuleLibraryManager.class);
  private static final String BUILD_FILE_NAME = "build.gradle";
  private final AtomicBoolean isUpdating = new AtomicBoolean(false);

  public AndroidModuleLibraryManager(@NotNull Project project) {
    super(project);
  }

  public void update() {
    doGradleSync(getProject(), (Project x) -> updateAndroidLibraryContent(x));
  }

  private Void updateAndroidLibraryContent(@NotNull Project androidProject) {
    LibraryTable androidProjectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(androidProject);
    Library[] androidProjectLibraries = androidProjectLibraryTable.getLibraries();
    if (androidProjectLibraries.length == 0) {
      LOG.warn("Gradle sync was incomplete -- no Android libraries found");
      return null;
    }
    HashSet<String> urls = new HashSet<>();
    for (Library refLibrary : androidProjectLibraries) {
      urls.addAll(Arrays.asList(refLibrary.getRootProvider().getUrls(OrderRootType.CLASSES)));
    }
    updateLibraryContent(urls);
    return null;
  }

  @NotNull
  @Override
  protected String getLibraryName() {
    return LIBRARY_NAME;
  }

  @NotNull
  @Override
  protected PersistentLibraryKind<AndroidModuleLibraryProperties> getLibraryKind() {
    return LIBRARY_KIND;
  }

  private void scheduleUpdate() {
    if (isUpdating.get()) {
      return;
    }

    final Runnable runnable = this::updateAndroidLibraries;
    DumbService.getInstance(getProject()).smartInvokeLater(runnable, ModalityState.NON_MODAL);
  }

  private void updateAndroidLibraries() {
    if (!isUpdating.compareAndSet(false, true)) {
      return;
    }
    update();
  }

  private void doGradleSync(Project flutterProject, Function<Project, Void> callback) {
    // TODO(messick): Collect URLs for all Android modules, including those within plugins.
    VirtualFile dir = flutterProject.getBaseDir().findChild("android");
    if (dir == null) dir = flutterProject.getBaseDir().findChild(".android"); // For modules.
    assert (dir != null);
    EmbeddedAndroidProject androidProject = new EmbeddedAndroidProject(FileUtilRt.toSystemIndependentName(dir.getPath()), null);
    androidProject.init();

    GradleSyncListener listener = new GradleSyncListener() {
      @SuppressWarnings("override")
      public void syncTaskCreated(@NotNull Project project, @NotNull GradleSyncInvoker.Request request) {}

      @Override
      public void syncStarted(@NotNull Project project, boolean skipped, boolean sourceGenerationRequested) {}

      @Override
      public void setupStarted(@NotNull Project project) {}

      @Override
      public void syncSucceeded(@NotNull Project project) {
        callback.apply(androidProject);
        isUpdating.set(false);
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

    GradleSyncInvoker.Request request = GradleSyncInvoker.Request.projectLoaded();
    request.runInBackground = true;
    GradleSyncInvoker gradleSyncInvoker = ServiceManager.getService(GradleSyncInvoker.class);
    gradleSyncInvoker.requestProjectSync(androidProject, request, listener);
  }

  @NotNull
  public static AndroidModuleLibraryManager getInstance(@NotNull final Project project) {
    return ServiceManager.getService(project, AndroidModuleLibraryManager.class);
  }

  public static void startWatching(@NotNull Project project) {
    // Start a process to monitor changes to Android dependencies and update the library content.
    // This loop is for debugging, not production.
    if (project.isDefault()) {
      return;
    }
    if (FlutterSdkUtil.hasFlutterModules(project)) {
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

      project.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
        @Override
        public void rootsChanged(ModuleRootEvent event) {
          manager.scheduleUpdate();
        }
      });
      manager.scheduleUpdate();
    }
  }

  private static void fileChanged(@NotNull final Project project, @NotNull final VirtualFile file) {
    if (!BUILD_FILE_NAME.equals(file.getName())) {
      return;
    }
    if (LocalFileSystem.getInstance() != file.getFileSystem() && !ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }
    if (!VfsUtilCore.isAncestor(project.getBaseDir(), file, true)) {
      return;
    }
    getInstance(project).scheduleUpdate();
  }

  private static class EmbeddedAndroidProject extends ProjectImpl {
    protected EmbeddedAndroidProject(@NotNull String filePath, @Nullable String projectName) {
      super(filePath, projectName);
    }
  }
}
