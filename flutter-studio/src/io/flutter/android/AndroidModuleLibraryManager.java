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
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
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
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleExecutionSettings;
import static com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType.RESOLVE_PROJECT;
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
    Project androidProject = doGradleSync(getProject());
    LibraryTable androidProjectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(androidProject);
    Library[] androidProjectLibraries = androidProjectLibraryTable.getLibraries();
    if (androidProjectLibraries.length == 0) {
      LOG.error("Gradle sync was incomplete -- no Android libraries found");
      return;
    }
    HashSet<String> urls = new HashSet<>();
    for (Library refLibrary : androidProjectLibraries) {
      urls.addAll(Arrays.asList(refLibrary.getRootProvider().getUrls(OrderRootType.CLASSES)));
    }
    updateLibraryContent(urls);
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

    try {
      update();
    }
    finally {
      isUpdating.set(false);
    }
  }

  public static void startWatching() {
    // Start a process to monitor changes to Android dependencies and update the library content.
    // This loop is for debugging, not production.
    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
      if (project.isDefault()) {
        continue;
      }
      if (FlutterSdkUtil.hasFlutterModules(project)) {
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
            new AndroidModuleLibraryManager(project).scheduleUpdate();
          }
        });
        new AndroidModuleLibraryManager(project).scheduleUpdate();
      }
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
    new AndroidModuleLibraryManager(project).scheduleUpdate();
  }

  private static Project doGradleSync(Project flutterProject) {
    // TODO(messick): Collect URLs for all Android modules, including within plugins.
    VirtualFile dir = flutterProject.getBaseDir().findChild("android");
    assert (dir != null);
    EmbeddedAndroidProject androidProject = new EmbeddedAndroidProject(FileUtilRt.toSystemIndependentName(dir.getPath()), null);
    androidProject.init();

    GradleSyncListener listener = null;
    GradleSyncInvoker.Request request = GradleSyncInvoker.Request.userRequest();
    request.runInBackground = false;
    GradleSyncInvoker gradleSyncInvoker = ServiceManager.getService(GradleSyncInvoker.class);
    gradleSyncInvoker.requestProjectSync(androidProject, request, listener);
    return androidProject;
  }

  private static class EmbeddedAndroidProject extends ProjectImpl {
    protected EmbeddedAndroidProject(@NotNull String filePath, @Nullable String projectName) {
      super(filePath, projectName);
    }
  }
}
