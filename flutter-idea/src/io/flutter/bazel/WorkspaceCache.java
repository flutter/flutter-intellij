/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.bazel;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import io.flutter.FlutterUtils;
import io.flutter.project.ProjectWatch;
import io.flutter.utils.FileWatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

/**
 * Holds the current Bazel workspace for a Project.
 * <p>
 * <p>Automatically reloads the workspace when out of date.
 */
public class WorkspaceCache {
  @NotNull private final Project project;
  @Nullable private Workspace cache;
  private boolean disconnected = false;

  private boolean refreshScheduled = false;

  private final Set<Runnable> subscribers = new LinkedHashSet<>();

  private WorkspaceCache(@NotNull final Project project) {
    this.project = project;

    // Do not attempt to set the WorkspaceCache unless the user has configured the
    // dartProjectsWithoutPubspecRegistryKey registry key.
    //
    // https://github.com/flutter/flutter-intellij/issues/7333
    if(Registry.is(dartProjectsWithoutPubspecRegistryKey, false)) {
      this.cache = null;
      return;
    }

    // Trigger a reload when file dependencies change.
    final AtomicReference<FileWatch> fileWatch = new AtomicReference<>();
    subscribe(() -> {
      if (project.isDisposed()) return;

      final Workspace next = cache;

      FileWatch nextWatch = null;
      if (next != null) {
        nextWatch = FileWatch.subscribe(next.getRoot(), next.getDependencies(), this::scheduleRefresh);
        nextWatch.setDisposeParent(project);
      }

      final FileWatch prevWatch = fileWatch.getAndSet(nextWatch);
      if (prevWatch != null) prevWatch.unsubscribe();
    });

    // Detect module root changes.
    ProjectWatch.subscribe(project, this::scheduleRefresh);

    // Load initial value.
    refresh();
  }

  private void scheduleRefresh() {
    if (refreshScheduled) {
      return;
    }
    refreshScheduled = true;
    SwingUtilities.invokeLater(() -> {
      refreshScheduled = false;
      if (project.isDisposed()) {
        return;
      }
      refresh();
    });
  }

  @NotNull
  public static WorkspaceCache getInstance(@NotNull final Project project) {
    return requireNonNull(project.getService(WorkspaceCache.class));
  }

  /**
   * Returns the Workspace in the cache.
   * <p>
   * <p>Returning a null means there is no current workspace for this project.
   */
  @Nullable
  public Workspace get() {
    return cache;
  }

  /**
   * Returns whether the  project is a bazel project.
   */
  public boolean isBazel() {
    return cache != null;
  }

  /**
   * Runs a callback each time the current Workspace changes.
   */
  public void subscribe(Runnable callback) {
    synchronized (subscribers) {
      subscribers.add(callback);
    }
  }

  /**
   * Stops notifications to a callback passed to {@link #subscribe}.
   */
  public void unsubscribe(Runnable callback) {
    synchronized (subscribers) {
      subscribers.remove(callback);
    }
  }

  /**
   * The Dart plugin uses this registry key to avoid bazel users getting their settings overridden on projects that include a
   * pubspec.yaml.
   * <p>
   * In other words, this key tells the plugin to configure dart projects without pubspec.yaml.
   */
  private static final String dartProjectsWithoutPubspecRegistryKey = "dart.projects.without.pubspec";

  /**
   * Executes a cache refresh.
   */
  private void refresh() {
    final Workspace workspace = Workspace.loadUncached(project);
    if (workspace == cache && !disconnected) return;
    if (cache != null && workspace == null) {
      disconnected = true;
      return;
    }

    disconnected = false;
    cache = workspace;

    notifyListeners();
  }

  private Set<Runnable> getSubscribers() {
    synchronized (subscribers) {
      return ImmutableSet.copyOf(subscribers);
    }
  }

  private void notifyListeners() {
    if (project.isDisposed()) {
      return;
    }
    for (Runnable sub : getSubscribers()) {
      try {
        sub.run();
      }
      catch (Exception e) {
        if (!Objects.equal(e.getMessage(), "expected failure in test")) {
          FlutterUtils.warn(LOG, "A subscriber to a WorkspaceCache threw an exception", e);
        }
      }
    }
  }

  private static final Logger LOG = Logger.getInstance(WorkspaceCache.class);
}
