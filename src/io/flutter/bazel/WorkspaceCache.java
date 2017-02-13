/*
 * Copyright  2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.bazel;

import com.google.common.base.Objects;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.flutter.project.ProjectWatch;
import io.flutter.utils.FileWatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the current Bazel workspace for a Project.
 *
 * <p>Automatically reloads the workspace when out of date.
 */
public abstract class WorkspaceCache {

  public static WorkspaceCache getInstance(Project project) {
    return project.getComponent(WorkspaceCache.class);
  }

  /**
   * Returns the Workspace in the cache.
   *
   * <p>Returning a null means there is no current workspace for this project.
   *
   * <p>If the cache hasn't loaded yet, blocks until it's ready.
   * Otherwise doesn't block.
   *
   * <p>To find out about Workspace changes, use a WorkspaceWatch.
   */
  public abstract @Nullable Workspace getValue();

  /**
   * Blocks until the refresh task finishes.
   * (For testing.)
   */
  public abstract void waitForIdle();

  abstract void subscribe(WorkspaceWatch ww);

  abstract void unsubscribe(WorkspaceWatch ww);

  @SuppressWarnings("ComponentNotRegistered") // Inspection doesn't work for inner class.
  private static class Impl extends WorkspaceCache implements ProjectComponent {
    private static final long MIN_MILLIS_BETWEEN_REFRESHES = 50;

    private final Project project;
    private final AtomicReference<Workspace> cache = new AtomicReference<>();
    private final FutureTask cacheReady = new FutureTask<>(() -> null);

    private final AtomicBoolean needRefresh = new AtomicBoolean();
    private final AtomicReference<FutureTask<Workspace>> refreshTask = new AtomicReference<>();
    private final Stopwatch timeSinceRefreshStart = Stopwatch.createUnstarted();

    private final AtomicBoolean closed = new AtomicBoolean();

    private final AtomicReference<FileWatch> fileWatch = new AtomicReference<>();

    /**
     * Subscriber list. (Access should be synchronized.)
     */
    private final Set<WorkspaceWatch> subscribers = new LinkedHashSet<>();

    private Impl(Project project) {
      this.project = project;
    }

    @Override
    public Workspace getValue() {
      try {
        cacheReady.get();
      }
      catch (Exception e) {
        LOG.warn("Unexpected exception waiting for cache to be ready", e);
        return null;
      }
      return cache.get();
    }

    public void waitForIdle() {
      final Future task = refreshTask.get();
      if (task == null) return;

      try {
        task.get();
      } catch (Exception e) {
        LOG.warn("Unexpected exception waiting for refresh task to be idle", e);
      }
    }

    @Override
    synchronized void subscribe(WorkspaceWatch ww) {
      subscribers.add(ww);
    }

    @Override
    synchronized void unsubscribe(WorkspaceWatch ww) {
      subscribers.remove(ww);
    }

    @Override
    public void initComponent() {

    }

    @Override
    public void projectOpened() {
      refreshAsync(); // Load initial value.
      ProjectWatch.subscribe(project, this::refreshAsync); // Detect module root changes.
    }

    @Override
    public void projectClosed() {
      closed.set(true);
      refreshAsync(); // Deliver end-of-stream and unsubscribe all.
    }

    @Override
    public void disposeComponent() {

    }

    @NotNull
    @Override
    public String getComponentName() {
      return "WorkspaceCache";
    }

    /**
     * Triggers a cache refresh.
     *
     * If a refresh is already in progress, schedules another one.
     */
    private void refreshAsync() {
      needRefresh.set(true);

      // Start up the background task if idle.
      final FutureTask<Workspace> newTask = new FutureTask<>(this::runRefresh, null);
      if (refreshTask.compareAndSet(null, newTask)) {
        AppExecutorUtil.getAppExecutorService().submit(newTask);
      }
    }

    private void runRefresh() {
      try {
        // Keep going until nobody asked for a refresh.
        while (needRefresh.get()) {

          // Throttle refreshes in case refreshAsync gets called multiple times quickly.
          if (timeSinceRefreshStart.isRunning()) {
            final long remaining = MIN_MILLIS_BETWEEN_REFRESHES - timeSinceRefreshStart.elapsed(TimeUnit.MILLISECONDS);
            if (remaining > 0) {
              try {
                Thread.sleep(remaining);
              }
              catch (InterruptedException e) {
                return;
              }
            }
          }

          needRefresh.set(false);
          timeSinceRefreshStart.reset().start();
          // Any refreshAsync() calls after this point will schedule another refresh.

          Workspace next = null;
          if (!closed.get()) {
            next = Workspace.load(this.project);
          }
          final boolean endOfStream = closed.get(); // Read again in case it changed during refresh.
          updateAndDeliver(endOfStream ? null : next, endOfStream);
        }
      } finally {
        refreshTask.set(null); // Allow restart on exit.
      }
    }

    private void updateAndDeliver(Workspace next, boolean endOfStream) {
      final Workspace prev = cache.getAndSet(next);
      cacheReady.run();
      if (Objects.equal(prev, next) && !endOfStream) return; // Debounce.

      // Update watched files.
      final FileWatch nextWatch = next == null ? null : FileWatch.subscribe(next.getRoot(), next.getDependencies(), this::refreshAsync);
      final FileWatch prevWatch = fileWatch.getAndSet(nextWatch);
      if (prevWatch != null) prevWatch.close();

      // We are on a background thread. Deliver events on the Swing thread.
      for (final WorkspaceWatch ww : getSubscribers()) {
        SwingUtilities.invokeLater(() -> ww.fireEvent(next, endOfStream));
      }
    }

    private synchronized Set<WorkspaceWatch> getSubscribers() {
      return ImmutableSet.copyOf(subscribers);
    }
  }

  private static final Logger LOG = Logger.getInstance(WorkspaceCache.class);
}
