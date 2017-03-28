/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Monitors the application library table to notify clients when Flutter SDK configuration changes.
 */
public class FlutterSdkManager {
  private final EventDispatcher<Listener> myDispatcher = EventDispatcher.create(Listener.class);
  private boolean isFlutterConfigured;

  @NotNull
  public static FlutterSdkManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, FlutterSdkManager.class);
  }

  private FlutterSdkManager(@NotNull Project project) {
    final LibraryTableListener libraryTableListener = new LibraryTableListener(project);
    ProjectLibraryTable.getInstance(project).addListener(libraryTableListener);

    final Timer timer = new Timer();
    timer.scheduleAtFixedRate(new TimerTask() {
      @Override
      public void run() {
        checkForFlutterSdkChange(project);
      }
    }, 1000, 1000);

    Disposer.register(project, () -> {
      ProjectLibraryTable.getInstance(project).removeListener(libraryTableListener);
      timer.cancel();
    });

    ProjectManager.getInstance().addProjectManagerListener(new ProjectManagerAdapter() {
      @Override
      public void projectOpened(@NotNull Project project) {
        checkForFlutterSdkChange(project);
      }

      @Override
      public void projectClosed(@NotNull Project project) {
        checkForFlutterSdkChange(project);
      }
    });

    // Cache initial state.
    isFlutterConfigured = isFlutterSdkSetAndNeeded(project);
  }

  // Send events if Flutter SDK was configured or unconfigured.
  public void checkForFlutterSdkChange(@NotNull Project project) {
    if (!isFlutterConfigured && isFlutterSdkSetAndNeeded(project)) {
      isFlutterConfigured = true;
      myDispatcher.getMulticaster().flutterSdkAdded();
    }
    else if (isFlutterConfigured && !isFlutterSdkSetAndNeeded(project)) {
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

  private boolean isFlutterSdkSetAndNeeded(@NotNull Project project) {
    return FlutterSdk.getFlutterSdk(project) != null && FlutterSdkUtil.hasFlutterModules(project);
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
    private @NotNull final Project myProject;

    LibraryTableListener(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public void afterLibraryAdded(Library newLibrary) {
      checkForFlutterSdkChange(myProject);
    }

    @Override
    public void afterLibraryRenamed(Library library) {
      // Since we key off name, test to be safe.
      checkForFlutterSdkChange(myProject);
    }

    @Override
    public void beforeLibraryRemoved(Library library) {
      // Test after.
    }

    @Override
    public void afterLibraryRemoved(Library library) {
      checkForFlutterSdkChange(myProject);
    }
  }
}
