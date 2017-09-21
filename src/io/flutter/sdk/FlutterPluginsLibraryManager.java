/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.impl.libraries.LibraryTableBase;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.jetbrains.lang.dart.util.DotPackagesFileUtil;
import io.flutter.pub.PubRoot;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.jetbrains.lang.dart.util.PubspecYamlUtil.PUBSPEC_YAML;

public class FlutterPluginsLibraryManager {
  private final Project project;

  private final AtomicBoolean isUpdating = new AtomicBoolean(false);

  public FlutterPluginsLibraryManager(@NotNull Project project) {
    this.project = project;
  }

  public void startWatching() {
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
        scheduleUpdate();
      }
    });

    scheduleUpdate();
  }

  private void fileChanged(@NotNull final Project project, @NotNull final VirtualFile file) {
    if (!DotPackagesFileUtil.DOT_PACKAGES.equals(file.getName())) return;
    if (LocalFileSystem.getInstance() != file.getFileSystem() && !ApplicationManager.getApplication().isUnitTestMode()) return;

    final VirtualFile parent = file.getParent();
    final VirtualFile pubspec = parent == null ? null : parent.findChild(PUBSPEC_YAML);

    if (pubspec != null) {
      scheduleUpdate();
    }
  }

  private void scheduleUpdate() {
    if (isUpdating.get()) {
      return;
    }

    final Runnable runnable = this::updateFlutterPlugins;
    DumbService.getInstance(project).smartInvokeLater(runnable, ModalityState.NON_MODAL);
  }

  private void updateFlutterPlugins() {
    if (!isUpdating.compareAndSet(false, true)) {
      return;
    }

    try {
      updateFlutterPluginsImpl();
    }
    finally {
      isUpdating.set(false);
    }
  }

  private void updateFlutterPluginsImpl() {
    final LibraryTable projectLibraryTable = ProjectLibraryTable.getInstance(project);
    final Library existingLibrary = projectLibraryTable.getLibraryByName(FlutterPluginLibraryType.FLUTTER_PLUGINS_LIBRARY_NAME);
    final Library library =
      existingLibrary != null ? existingLibrary
                              : WriteAction.compute(() -> {
                                final LibraryTableBase.ModifiableModel libTableModel =
                                  ProjectLibraryTable.getInstance(project).getModifiableModel();
                                final Library lib = libTableModel
                                  .createLibrary(FlutterPluginLibraryType.FLUTTER_PLUGINS_LIBRARY_NAME,
                                                 FlutterPluginLibraryType.LIBRARY_KIND);
                                libTableModel.commit();
                                return lib;
                              });

    final Set<String> flutterPluginPaths = getFlutterPluginPaths(PubRoot.multipleForProject(project));
    final Set<String> flutterPluginUrls = new HashSet<>();
    for (String path : flutterPluginPaths) {
      flutterPluginUrls.add(VfsUtilCore.pathToUrl(path));
    }
    final Set<String> existingUrls = new HashSet<>(Arrays.asList(library.getUrls(OrderRootType.CLASSES)));

    if (flutterPluginUrls.containsAll(existingUrls) && existingUrls.containsAll(flutterPluginUrls)) {
      // No changes needed.
      return;
    }

    ApplicationManager.getApplication().runWriteAction(() -> {
      final LibraryEx.ModifiableModelEx model = (LibraryEx.ModifiableModelEx)library.getModifiableModel();

      final Set<String> existingCopy = new HashSet<>(existingUrls);
      existingUrls.removeAll(flutterPluginUrls);
      flutterPluginUrls.removeAll(existingCopy);

      for (String url : existingUrls) {
        model.removeRoot(url, OrderRootType.CLASSES);
      }

      for (String url : flutterPluginUrls) {
        model.addRoot(url, OrderRootType.CLASSES);
      }

      model.commit();
    });

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (FlutterModuleUtils.isFlutterModule(module)) {
        addFlutterLibraryDependency(module, library);
      }
      else {
        removeFlutterLibraryDependency(module, library);
      }
    }
  }

  private Set<String> getFlutterPluginPaths(List<PubRoot> roots) {
    final Set<String> paths = new HashSet<>();

    for (PubRoot pubRoot : roots) {
      if (pubRoot.getPackages() == null) {
        continue;
      }

      final Map<String, String> map = DotPackagesFileUtil.getPackagesMap(pubRoot.getPackages());
      if (map == null) {
        continue;
      }

      for (String packagePath : map.values()) {
        final VirtualFile libFolder = LocalFileSystem.getInstance().findFileByPath(packagePath);
        if (libFolder == null) {
          continue;
        }
        final PubRoot pluginRoot = PubRoot.forDirectory(libFolder.getParent());
        if (pluginRoot == null) {
          continue;
        }

        if (pluginRoot.isFlutterPlugin()) {
          paths.add(pluginRoot.getPath());
        }
      }
    }

    return paths;
  }

  private static void addFlutterLibraryDependency(@NotNull final Module module, @NotNull final Library library) {
    final ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();

    try {
      for (final OrderEntry orderEntry : modifiableModel.getOrderEntries()) {
        if (orderEntry instanceof LibraryOrderEntry &&
            LibraryTablesRegistrar.PROJECT_LEVEL.equals(((LibraryOrderEntry)orderEntry).getLibraryLevel()) &&
            StringUtil.equals(library.getName(), ((LibraryOrderEntry)orderEntry).getLibraryName())) {
          return; // dependency already exists
        }
      }

      modifiableModel.addLibraryEntry(library);

      ApplicationManager.getApplication().runWriteAction(modifiableModel::commit);
    }
    finally {
      if (!modifiableModel.isDisposed()) {
        modifiableModel.dispose();
      }
    }
  }

  private void removeFlutterLibraryDependency(Module module, Library library) {
    final ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();

    try {
      boolean wasFound = false;

      for (final OrderEntry orderEntry : modifiableModel.getOrderEntries()) {
        if (orderEntry instanceof LibraryOrderEntry &&
            LibraryTablesRegistrar.PROJECT_LEVEL.equals(((LibraryOrderEntry)orderEntry).getLibraryLevel()) &&
            StringUtil.equals(library.getName(), ((LibraryOrderEntry)orderEntry).getLibraryName())) {
          wasFound = true;
          modifiableModel.removeOrderEntry(orderEntry);
        }
      }

      if (wasFound) {
        ApplicationManager.getApplication().runWriteAction(modifiableModel::commit);
      }
    }
    finally {
      if (!modifiableModel.isDisposed()) {
        modifiableModel.dispose();
      }
    }
  }
}
