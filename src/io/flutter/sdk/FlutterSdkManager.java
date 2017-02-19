/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.google.common.base.Objects;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.roots.impl.libraries.ApplicationLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Monitors the application library table to notify clients when Flutter SDK configuration changes.
 */
public class FlutterSdkManager {
  private final EventDispatcher<Listener> myDispatcher = EventDispatcher.create(Listener.class);
  private final LibraryTableListener myLibraryTableListener = new LibraryTableListener();
  private final RootProvider.RootSetChangedListener rootListener = x -> checkForFlutterSdkChange();
  private boolean isFlutterConfigured;

  @NotNull
  public static FlutterSdkManager getInstance() {
    return ServiceManager.getService(FlutterSdkManager.class);
  }

  private FlutterSdkManager() {
    listenForSdkChanges();
    // Cache initial state.
    isFlutterConfigured = isGlobalFlutterSdkSetAndNeeded();
  }

  private void listenForSdkChanges() {
    ApplicationLibraryTable.getApplicationTable().addListener(myLibraryTableListener);

    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      @Override
      public void projectOpened(@NotNull Project project) {
        checkForFlutterSdkChange();
      }

      @Override
      public void projectClosed(@NotNull Project project) {
        checkForFlutterSdkChange();
      }
    });

    // The Dart plugin modifies the library in place, so we need to listen for its root changes.
    for (Library library : ApplicationLibraryTable.getApplicationTable().getLibraries()) {
      watchDartSdkRoots(library);
    }
  }

  // Send events if Flutter SDK was configured or unconfigured.
  public void checkForFlutterSdkChange() {
    if (!isFlutterConfigured && isGlobalFlutterSdkSetAndNeeded()) {
      isFlutterConfigured = true;
      myDispatcher.getMulticaster().flutterSdkAdded();
    } else if (isFlutterConfigured && !isGlobalFlutterSdkSetAndNeeded()) {
      isFlutterConfigured = false;
      myDispatcher.getMulticaster().flutterSdkRemoved();
    }
  }

  public void addListener(@NotNull Listener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeListener(@NotNull Listener listener) {
    myDispatcher.removeListener(listener);
  }

  private void watchDartSdkRoots(Library library) {
    final RootProvider provider = library.getRootProvider();
    if (Objects.equal(library.getName(), "Dart SDK")) {
      provider.addRootSetChangedListener(rootListener);
    } else {
      provider.removeRootSetChangedListener(rootListener);
    }
  }

  private static boolean isGlobalFlutterSdkSetAndNeeded() {
    return FlutterSdk.getGlobalFlutterSdk() != null && FlutterSdkUtil.hasFlutterModules();
  }

  /**
   * Listen for SDK configuration changes.
   */
  public interface Listener extends EventListener {

    /**
     * Fired when the Flutter global library is set.
     */
    void flutterSdkAdded();

    /**
     * Fired when the Flutter global library is removed.
     */
    void flutterSdkRemoved();
  }

  // Listens for changes in Flutter Library configuration state in the Library table.
  private final class LibraryTableListener implements LibraryTable.Listener {

    @Override
    public void afterLibraryAdded(Library newLibrary) {
      checkForFlutterSdkChange();
      watchDartSdkRoots(newLibrary);
    }

    @Override
    public void afterLibraryRenamed(Library library) {
      // Since we key off name, test to be safe.
      checkForFlutterSdkChange();
      watchDartSdkRoots(library);
    }

    @Override
    public void beforeLibraryRemoved(Library library) {
      // Test after.
    }

    @Override
    public void afterLibraryRemoved(Library library) {
      library.getRootProvider().removeRootSetChangedListener(rootListener);
      checkForFlutterSdkChange();
    }
  }
}
