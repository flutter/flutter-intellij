/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

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
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;

// TODO: run this on pubspec file changes and project structure changes (new modules, removed modules, ...)

// TODO: or, just watch for changes to the .packages file?

public class FlutterPluginsLibraryManager {

  public static void updatePlugins(@NotNull final Project project) {
    final Runnable runnable = () -> {
      updateFlutterPlugins(project);
    };

    DumbService.getInstance(project).smartInvokeLater(runnable, ModalityState.NON_MODAL);
  }

  @NotNull
  private static Library updateFlutterPlugins(@NotNull final Project project) {
    // , @NotNull final DartFileListener.DartLibInfo libInfo

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

    final String[] existingUrls = library.getUrls(OrderRootType.CLASSES);

    //final Collection<String> libRootUrls = libInfo.getLibRootUrls();

    //if ((!libInfo.isProjectWithoutPubspec() && isBrokenPackageMap(((LibraryEx)library).getProperties())) ||
    //    existingUrls.length != libRootUrls.size() ||
    //    !libRootUrls.containsAll(Arrays.asList(existingUrls))) {
    ApplicationManager.getApplication().runWriteAction(() -> {
      final LibraryEx.ModifiableModelEx model = (LibraryEx.ModifiableModelEx)library.getModifiableModel();

      //for (String url : existingUrls) {
      //  model.removeRoot(url, OrderRootType.CLASSES);
      //}
      //
      ////for (String url : libRootUrls) {
      ////  model.addRoot(url, OrderRootType.CLASSES);
      ////}
      //
      //final FlutterPluginLibraryProperties properties = new FlutterPluginLibraryProperties();
      //properties.setPath("/Users/devoncarew/.pub-cache/hosted/pub.dartlang.org/flutter_blue-0.2.2/");
      //model.setProperties(properties);

      model.commit();
    });
    //}

    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (FlutterModuleUtils.isFlutterModule(module)) {
        addFlutterLibraryDependency(module, library);
      }
      // TODO: else, remove it?
    }

    return library;
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
}
