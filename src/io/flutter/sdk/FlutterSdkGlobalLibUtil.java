/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.ApplicationLibraryTable;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FlutterSdkGlobalLibUtil {

  private static final Logger LOG = Logger.getInstance(FlutterSdkGlobalLibUtil.class.getName());

  public static void ensureFlutterSdkConfigured(@NotNull final String sdkHomePath) {
    final Library library = ApplicationLibraryTable.getApplicationTable().getLibraryByName(FlutterSdk.FLUTTER_SDK_GLOBAL_LIB_NAME);
    if (library == null) {
      final LibraryTable.ModifiableModel model = ApplicationLibraryTable.getApplicationTable().getModifiableModel();
      createFlutterSdkGlobalLib(model, sdkHomePath);
      model.commit();
    }
    else {
      final FlutterSdk sdk = FlutterSdk.getSdkByLibrary(library);
      if (sdk == null || !sdkHomePath.equals(sdk.getHomePath())) {
        setupFlutterSdkRoots(library, sdkHomePath);
      }
    }
  }

  private static void createFlutterSdkGlobalLib(@NotNull final LibraryTable.ModifiableModel libraryTableModel,
                                                @NotNull final String sdkHomePath) {
    final Library existingLib = libraryTableModel.getLibraryByName(FlutterSdk.FLUTTER_SDK_GLOBAL_LIB_NAME);
    if (existingLib != null) {
      setupFlutterSdkRoots(existingLib, sdkHomePath);
    }
    else {
      final Library library = libraryTableModel.createLibrary(FlutterSdk.FLUTTER_SDK_GLOBAL_LIB_NAME);
      setupFlutterSdkRoots(library, sdkHomePath);
    }
  }

  @SuppressWarnings("Duplicates")
  private static void setupFlutterSdkRoots(@NotNull final Library library, @NotNull final String sdkHomePath) {
    final VirtualFile libRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(sdkHomePath + "/bin/cache/dart-sdk/lib");
    if (libRoot != null && libRoot.isDirectory()) {
      final LibraryEx.ModifiableModelEx libModifiableModel = (LibraryEx.ModifiableModelEx)library.getModifiableModel();
      try {
        // remove old
        for (String url : libModifiableModel.getUrls(OrderRootType.CLASSES)) {
          libModifiableModel.removeRoot(url, OrderRootType.CLASSES);
        }
        for (String url : libModifiableModel.getExcludedRootUrls()) {
          libModifiableModel.removeExcludedRoot(url);
        }

        // add new
        libModifiableModel.addRoot(libRoot, OrderRootType.CLASSES);

        libRoot.refresh(false, true);
        for (final VirtualFile subFolder : libRoot.getChildren()) {
          if (subFolder.getName().startsWith("_")) {
            libModifiableModel.addExcludedRoot(subFolder.getUrl());
          }
        }

        libModifiableModel.commit();
      }
      catch (Exception e) {
        LOG.warn(e);
        Disposer.dispose(libModifiableModel);
      }
    }
  }

  public static void ensureDartSdkConfigured(@NotNull final String sdkHomePath) {
    // TODO Implement or remove.
  }

  public static void enableDartSdk(Module module) {
    // TODO Implement enableDartSdk()
  }

  public static void disableDartSdk(List<Module> modules) {
    // TODO Implement disableDartSdk()
  }

  public static void enableFlutterSdk(@NotNull final Module module) {
    if (isFlutterSdkEnabled(module)) return;

    final ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
    try {
      modifiableModel.addInvalidLibrary(FlutterSdk.FLUTTER_SDK_GLOBAL_LIB_NAME, LibraryTablesRegistrar.APPLICATION_LEVEL);
      modifiableModel.commit();
    }
    catch (Exception e) {
      LOG.warn(e);
      if (!modifiableModel.isDisposed()) modifiableModel.dispose();
    }
  }

  public static boolean isFlutterSdkEnabled(@NotNull final Module module) {
    for (final OrderEntry orderEntry : ModuleRootManager.getInstance(module).getOrderEntries()) {
      if (isFlutterSdkOrderEntry(orderEntry)) {
        return true;
      }
    }

    return false;
  }

  private static boolean isFlutterSdkOrderEntry(@NotNull final OrderEntry orderEntry) {
    return orderEntry instanceof LibraryOrderEntry &&
           LibraryTablesRegistrar.APPLICATION_LEVEL.equals(((LibraryOrderEntry)orderEntry).getLibraryLevel()) &&
           FlutterSdk.FLUTTER_SDK_GLOBAL_LIB_NAME.equals(((LibraryOrderEntry)orderEntry).getLibraryName());
  }
}
