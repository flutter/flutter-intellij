/*
 * Copyright  2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.bazel;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Watches a project for Bazel workspace changes.
 *
 * <p>Each WorkspaceWatch instance represents one subscription.
 */
public class WorkspaceWatch implements Closeable {
  private final @NotNull Project project;
  private final @NotNull Consumer<Workspace> callback;
  /**
   * The cache that delivers events to us. Non-null while subscribed.
   */
  private final AtomicReference<WorkspaceCache> cache = new AtomicReference<>();

  private WorkspaceWatch(@NotNull Project project, @NotNull Consumer<Workspace> callback, WorkspaceCache cache) {
    this.project = project;
    this.callback = callback;
    this.cache.set(cache);
  }

  /**
   * Subscribes to updates to a workspace in the given project.
   **
   * <p>The Consumer will be called each time the workspace changes (added, moved
   * or deleted). If there is no current workspace, the argument will be null.
   *
   * <p>To unsubscribe, call close(). The watch will automatically be unsubscribed when the
   * Project is closed or the callback throws an exception.
   *
   * <p>Events will be delivered asynchronously on the Swing event thread.</p>
   */
  public static @NotNull WorkspaceWatch subscribe(Project project, Consumer<Workspace> callback) {
    final WorkspaceCache cache = project.getComponent(WorkspaceCache.class);
    final WorkspaceWatch ww = new WorkspaceWatch(project, callback, cache);
    cache.subscribe(ww);
    return ww;
  }

  /**
   * Returns the currently cached value of the Bazel Workspace.
   *
   * <p>A null means that there is no current workspace (or we have unsubscribed).
   */
  public @Nullable Workspace get() {
    final WorkspaceCache cache = this.cache.get();
    return cache == null ? null : cache.getValue();
  }

  /**
   * Unsubscribe from events.
   */
  @Override
  public void close() {
    final WorkspaceCache cache = this.cache.getAndSet(null);
    if (cache != null) cache.unsubscribe(this);
  }

  void fireEvent(Workspace w, boolean endOfStream) {
    try {
      callback.accept(w);
    } catch (Exception e) {
      LOG.warn("WorkspaceWatch callback threw exception", e);
      endOfStream = true; // Unsubscribe to avoid more exceptions.
    }
    if (endOfStream) close();
  }

  private static final Logger LOG = Logger.getInstance(WorkspaceWatch.class);
}
