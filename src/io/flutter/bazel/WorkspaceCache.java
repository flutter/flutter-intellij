/*
 * Copyright  2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.bazel;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import io.flutter.project.ProjectWatch;
import io.flutter.utils.FileWatch;
import io.flutter.utils.Refreshable;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the current Bazel workspace for a Project.
 *
 * <p>Automatically reloads the workspace when out of date.
 */
public class WorkspaceCache {
  private final Project project;
  private final Refreshable<Workspace> cache;

  private WorkspaceCache(Project project) {
    this.project = project;

    this.cache = new Refreshable<>();
    cache.setDisposeParent(project);

    // Trigger a reload when file dependencies change.
    final AtomicReference<FileWatch> fileWatch = new AtomicReference<>();
    cache.subscribe(() -> {
      if (project.isDisposed()) return;

      final Workspace next = cache.getNow();

      FileWatch nextWatch = null;
      if (next != null) {
        nextWatch = FileWatch.subscribe(next.getRoot(), next.getDependencies(), this::refreshAsync);
        nextWatch.setDisposeParent(project);
      }

      final FileWatch prevWatch = fileWatch.getAndSet(nextWatch);
      if (prevWatch != null) prevWatch.unsubscribe();
    });

    ProjectWatch.subscribe(project, this::refreshAsync); // Detect module root changes.

    // Load initial value.
    refreshAsync();
  }

  public static WorkspaceCache getInstance(Project project) {
    return ServiceManager.getService(project, WorkspaceCache.class);
  }

  /**
   * Returns the Workspace in the cache.
   *
   * <p>Returning a null means there is no current workspace for this project.
   *
   * <p>If the cache hasn't loaded yet, blocks until it's ready.
   * Otherwise doesn't block.
   */
  public @Nullable Workspace getNow() {
    return cache.getNow();
  }

  /**
   * Waits for any refreshes to finish, then returns the new workspace (or null).
   *
   * @throws IllegalStateException if called on the Swing dispatch thread.
   */
  public @Nullable Workspace getWhenReady() {
    return cache.getWhenReady();
  }

  /**
   * Runs a callback each time the current Workspace changes.
   */
  public void subscribe(Runnable callback) {
    cache.subscribe(callback);
  }

  /**
   * Stops notifications to a callback passed to {@link #subscribe}.
   */
  public void unsubscribe(Runnable callback) {
    cache.unsubscribe(callback);
  }

  /**
   * Triggers a cache refresh.
   *
   * If a refresh is already in progress, schedules another one.
   */
  private void refreshAsync() {
    cache.refresh(() -> Workspace.load(project));
  }

  private static final Logger LOG = Logger.getInstance(WorkspaceCache.class);
}
