/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.android;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.impl.stores.StoreUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryTableBase;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GradleDependencyMerger {
  private static String ANDROID_LIBRARY_NAME = "Android Libraries";

  private final Project flutterProject;
  private final Project androidProject;

  protected GradleDependencyMerger(@NotNull Project project) {
    flutterProject = project;
    androidProject = makeAndroidProject(project);
  }

  private void process() {
    doGradleSync();
    mergeAndroidLibrariesIntoFlutterProject();
    //JavaProjectModelModificationService.getInstance(project).addDependency(myCurrentModule, myLibrary, myScope, myExported);
  }

  private void mergeAndroidLibrariesIntoFlutterProject() {
    LibraryTable androidProjectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(androidProject);
    Library[] androidProjectLibraries = androidProjectLibraryTable.getLibraries();
    assert (androidProjectLibraries.length > 0);
    new WriteAction<Void>() {
      @Override
      protected void run(@NotNull final Result<Void> result) {
        updateFlutterProjectWithAndroidLibraryRoots(androidProjectLibraries);
        //addSyntheticLibrary(project, library);
        StoreUtil.saveProject(flutterProject, true);
        // See ProjectManagerImpl.closeProject(); need to be in write-safe context.
        //androidProject.dispose();
      }
    }.execute();
  }

  private void updateFlutterProjectWithAndroidLibraryRoots(Library[] libraries) {
    Library library = getAndroidLibrary(flutterProject, ProjectLibraryTable.getInstance(flutterProject));
    String[] existingUrls = library.getUrls(OrderRootType.CLASSES);
    ApplicationManager.getApplication().runWriteAction(() -> {
      final LibraryEx.ModifiableModelEx model = (LibraryEx.ModifiableModelEx)library.getModifiableModel();
      for (String url : existingUrls) {
        model.removeRoot(url, OrderRootType.CLASSES);
      }

      for (Library refLibrary : libraries) {
        for (OrderRootType rootType : new OrderRootType[]{OrderRootType.CLASSES, OrderRootType.SOURCES}) {
          for (String url : refLibrary.getRootProvider().getUrls(rootType)) {
            model.addRoot(url, rootType);
          }
        }
      }

      model.commit();
    });
  }

  private void doGradleSync() {
    GradleSyncListener listener = null;
    GradleSyncInvoker.Request request = GradleSyncInvoker.Request.projectLoaded();
    request.generateSourcesOnSuccess = false;
    request.useCachedGradleModels = false;
    request.runInBackground = false;
    GradleSyncInvoker gradleSyncInvoker = ServiceManager.getService(GradleSyncInvoker.class);
    gradleSyncInvoker.requestProjectSync(androidProject, request, listener);
  }

  public static void process(@NotNull Project project) {
    new GradleDependencyMerger(project).process();
  }

  private static Library getAndroidLibrary(Project flutterProject, LibraryTable projectLibraryTable) {
    Library existingLibrary = projectLibraryTable.getLibraryByName(ANDROID_LIBRARY_NAME);
    if (existingLibrary != null) {
      return existingLibrary;
    }
    return WriteAction.compute(() -> {
      LibraryTableBase.ModifiableModel libTableModel = ProjectLibraryTable.getInstance(flutterProject).getModifiableModel();
      Library lib = libTableModel.createLibrary(ANDROID_LIBRARY_NAME);
      libTableModel.commit();
      return lib;
    });
  }

  @NotNull
  private static Project makeAndroidProject(@NotNull Project flutterProject) {
    VirtualFile dir = flutterProject.getBaseDir().findChild("android");
    assert (dir != null);
    EmbeddedAndroidProject project = new EmbeddedAndroidProject(FileUtilRt.toSystemIndependentName(dir.getPath()), null);
    project.init();
    return project;
  }

  private static class EmbeddedAndroidProject extends ProjectImpl {
    protected EmbeddedAndroidProject(@NotNull String filePath, @Nullable String projectName) {
      super(filePath, projectName);
    }
  }
}
