/*
 * Copyright  2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.intellij.ProjectTopics;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Watches a project for module root changes.
 *
 * <p>Each ProjectWatch instance represents one subscription.
 */
public class ProjectWatch implements Closeable {
  private final @NotNull Runnable callback;

  // Value is null when unsubscribed.
  private final AtomicReference<Runnable> unsubscribe = new AtomicReference<>();

  private ProjectWatch(@NotNull Project project, @NotNull Runnable callback) {
    this.callback = callback;

    final ProjectManagerListener listener = new ProjectManagerAdapter() {
      @Override
      public void projectClosed(Project project) {
        fireEvent();
      }
    };

    final ProjectManager manager = ProjectManager.getInstance();
    manager.addProjectManagerListener(project, listener);

    final MessageBusConnection bus = project.getMessageBus().connect();
    bus.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        fireEvent();
      }
    });

    unsubscribe.set(() -> {
      bus.disconnect();
      manager.removeProjectManagerListener(project, listener);
    });
  }

  /**
   * Subscribes to project changes. This includes module root changes and closing the project.
   */
  public static @NotNull ProjectWatch subscribe(@NotNull Project project, @NotNull Runnable callback) {
    return new ProjectWatch(project, callback);
  }

  /**
   * Unsubscribes this ProjectWatch from events.
   */
  @Override
  public void close() {
    final Runnable unsubscribe = this.unsubscribe.getAndSet(null);
    if (unsubscribe != null) {
      unsubscribe.run();
    }
  }

  private void fireEvent() {
    if (unsubscribe.get() == null) return;

    try {
      callback.run();
    } catch (Exception e) {
      LOG.error("Uncaught exception in ProjectWatch callback", e);
      close(); // avoid further errors
    }
  }

  private static final Logger LOG = Logger.getInstance(ProjectWatch.class);
}
