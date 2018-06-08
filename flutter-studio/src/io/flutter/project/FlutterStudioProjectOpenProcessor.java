/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.android.tools.idea.apk.ApkFacet;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.hash.HashSet;
import io.flutter.FlutterUtils;
import io.flutter.android.GradleDependencyFetcher;
import io.flutter.pub.PubRoot;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FlutterStudioProjectOpenProcessor extends FlutterProjectOpenProcessor {
  @Override
  public String getName() {
    return "Flutter Studio";
  }

  @Override
  public boolean canOpenProject(@Nullable VirtualFile file) {
    if (file == null) return false;
    ApplicationInfo info = ApplicationInfo.getInstance();
    final PubRoot root = PubRoot.forDirectory(file);
    return root != null && root.declaresFlutter();
  }

  @Nullable
  @Override
  public Project doOpenProject(@NotNull VirtualFile virtualFile, @Nullable Project projectToClose, boolean forceOpenInNewFrame) {
    if (super.doOpenProject(virtualFile, projectToClose, forceOpenInNewFrame) == null) return null;
    // The superclass may have caused the project to be reloaded. Find the new Project object.
    Project project = FlutterUtils.findProject(virtualFile.getPath());
    if (project != null) {
      FlutterProjectCreator.disableUserConfig(project);
      addModuleRoots(project);
      return project;
    }
    return null;
  }

  private static void addModuleRoots(@NotNull Project project) {
    ModuleManager mgr = ModuleManager.getInstance(project);
    for (Module module : mgr.getModules()) {
      if (FlutterModuleUtils.isFlutterModule(module)) {
        GradleDependencyFetcher fetcher = new GradleDependencyFetcher(project);
        fetcher.run(); // TODO(messick): Need to make this async.
        Map<String, List<String>> dependencies = fetcher.getDependencies();
        ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
        Set<String> paths = new HashSet<>();
        LocalFileSystem lfs = LocalFileSystem.getInstance();
        List<String> single = Collections.emptyList();
        for (List<String> list : dependencies.values()) {
          if (list.size() > single.size()) {
            single = list;
          }
        }
        LibraryTable table = model.getModuleLibraryTable();
        for (String dep : single) {
          Library library = table.createLibrary();
          Library.ModifiableModel libraryModifiableModel = library.getModifiableModel();
          libraryModifiableModel.addJarDirectory(dep, false);
        }
      }
    }
  }

  private static Library addProjectLibrary(final Module module, final String name, final List<String> jarDirectories, final VirtualFile[] sources) {
    return new WriteAction<Library>() {
      protected void run(@NotNull final Result<Library> result) {
        LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(module.getProject());
        Library library = libraryTable.getLibraryByName(name);
        if (library == null) {
          library = libraryTable.createLibrary(name);
          Library.ModifiableModel model = library.getModifiableModel();
          for (String path : jarDirectories) {
            String url = VfsUtilCore.pathToUrl(path);
            VirtualFileManager.getInstance().refreshAndFindFileByUrl(url);
            model.addJarDirectory(url, false);
          }
          for (VirtualFile sourceRoot : sources) {
            model.addRoot(sourceRoot, OrderRootType.SOURCES);
          }
          model.commit();
        }
        result.setResult(library);
      }
    }.execute().getResultObject();
  }
}
