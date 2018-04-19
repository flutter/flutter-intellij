/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Monitors the application library table to notify clients when Flutter SDK configuration changes.
 */
public class FlutterSdkManager {
  private final EventDispatcher<Listener> myListenerDispatcher = EventDispatcher.create(Listener.class);
  private boolean isFlutterConfigured;
  private final @NotNull Project myProject;

  @NotNull
  public static FlutterSdkManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, FlutterSdkManager.class);
  }

  private FlutterSdkManager(@NotNull Project project) {
    myProject = project;

    final LibraryTableListener libraryTableListener = new LibraryTableListener();
    ProjectLibraryTable.getInstance(project).addListener(libraryTableListener);

    // TODO(devoncarew): We should replace this polling solution with listeners to project
    // structure changes.
    final ScheduledFuture timer = JobScheduler.getScheduler().scheduleWithFixedDelay(
      this::checkForFlutterSdkChange, 1, 1, TimeUnit.SECONDS);

    Disposer.register(project, () -> {
      ProjectLibraryTable.getInstance(project).removeListener(libraryTableListener);
      timer.cancel(false);
    });

    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerListener() {
      @Override
      public void projectOpened(@NotNull Project project) {
        checkForFlutterSdkChange();
      }

      @Override
      public void projectClosed(@NotNull Project project) {
        checkForFlutterSdkChange();
      }
    });

    // Cache initial state.
    isFlutterConfigured = isFlutterSdkSetAndNeeded();
  }

  // Send events if Flutter SDK was configured or unconfigured.
  public void checkForFlutterSdkChange() {
    if (!isFlutterConfigured && isFlutterSdkSetAndNeeded()) {
      isFlutterConfigured = true;
      myListenerDispatcher.getMulticaster().flutterSdkAdded();
    }
    else if (isFlutterConfigured && !isFlutterSdkSetAndNeeded()) {
      isFlutterConfigured = false;
      myListenerDispatcher.getMulticaster().flutterSdkRemoved();
    }
  }

  public void addListener(@NotNull Listener listener) {
    myListenerDispatcher.addListener(listener);
  }

  public void removeListener(@NotNull Listener listener) {
    myListenerDispatcher.removeListener(listener);
  }

  private boolean isFlutterSdkSetAndNeeded() {
    return FlutterSdk.getFlutterSdk(myProject) != null && FlutterSdkUtil.hasFlutterModules(myProject);
  }

  /**
   * Listen for SDK configuration changes.
   */
  public interface Listener extends EventListener {
    /**
     * Fired when the Flutter global library is set.
     */
    default void flutterSdkAdded() {
    }

    /**
     * Fired when the Flutter global library is removed.
     */
    default void flutterSdkRemoved() {
    }
  }

  // Listens for changes in Flutter Library configuration state in the Library table.
  private final class LibraryTableListener implements LibraryTable.Listener {
    @Override
    public void afterLibraryAdded(@NotNull Library newLibrary) {
      checkForFlutterSdkChange();
    }

    @Override
    public void afterLibraryRenamed(@NotNull Library library) {
      // Since we key off name, test to be safe.
      checkForFlutterSdkChange();
    }

    @Override
    public void beforeLibraryRemoved(@NotNull Library library) {
      // Test after.
    }

    @Override
    public void afterLibraryRemoved(@NotNull Library library) {
      checkForFlutterSdkChange();
    }
  }
}
