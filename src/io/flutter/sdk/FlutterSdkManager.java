/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
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

  private static FlutterSdkManager INSTANCE;
  private final EventDispatcher<Listener> myDispatcher = EventDispatcher.create(Listener.class);
  private final LibraryTableListener myLibraryTableListener = new LibraryTableListener();
  private boolean isFlutterConfigured;

  private FlutterSdkManager() {
    listenForSdkChanges();
    // Cache initial state.
    isFlutterConfigured = isGlobalFlutterSdkSetAndNeeded();
  }

  // TODO(devoncarew): Use an app service singleton (ServiceManager.getService(project, ...))?
  public static FlutterSdkManager getInstance() {
    if (INSTANCE == null) {
      INSTANCE = new FlutterSdkManager();
    }
    return INSTANCE;
  }

  private void listenForSdkChanges() {
    ApplicationLibraryTable.getApplicationTable().addListener(myLibraryTableListener);
    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      @Override
      public void projectOpened(@NotNull Project project) {
        checkForFlutterSdkAddition();
      }
      @Override
      public void projectClosed(@NotNull Project project) {
        checkForFlutterSdkRemoval();
      }
    });
  }

  private void checkForFlutterSdkAddition() {
    if (!isFlutterConfigured && isGlobalFlutterSdkSetAndNeeded()) {
      isFlutterConfigured = true;
      myDispatcher.getMulticaster().flutterSdkAdded();
    }
  }

  private void checkForFlutterSdkRemoval() {
    if (isFlutterConfigured && !isGlobalFlutterSdkSetAndNeeded()) {
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
      checkForFlutterSdkAddition();
    }

    @Override
    public void afterLibraryRenamed(Library library) {
      // Since we key off name, test to be safe.
      checkForFlutterSdkRemoval();
    }

    @Override
    public void beforeLibraryRemoved(Library library) {
      // Test after.
    }

    @Override
    public void afterLibraryRemoved(Library library) {
      checkForFlutterSdkRemoval();
    }

  }
}
