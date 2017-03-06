/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.google.common.base.Splitter;
import com.google.common.collect.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Watches a set of VirtualFiles for changes.
 *
 * <p>Each FileWatch instance represents one subscription.
 *
 * <p>The callback will be called when the IntelliJ Platform notices the change,
 * which may be different from when it's changed on disk, due to caching.
 */
public class FileWatch {
  private final @NotNull ImmutableSet<Location> watched;
  private final @NotNull Runnable callback;

  /**
   * When true, no more events should be delivered.
   */
  private final AtomicBoolean unsubscribed = new AtomicBoolean();

  /**
   * The representation of this FileWatch in IntelliJ's dispose tree.
   *
   * <p>(Private so that nobody can add children.)
   */
  private final Disposable disposeLeaf;

  private FileWatch(@NotNull ImmutableSet<Location> watched, @NotNull Runnable callback) {
    this.watched = watched;
    this.callback = callback;
    this.disposeLeaf = this::unsubscribe;
  }

  /**
   * Starts watching a single file or directory.
   */
  public static @NotNull FileWatch subscribe(@NotNull VirtualFile file, @NotNull Runnable callback) {
    final FileWatch watcher =  new FileWatch(ImmutableSet.of(new Location(file, null)), callback);
    subscriptions.subscribe(watcher);
    return watcher;
  }

  /**
   * Starts watching some paths beneath a VirtualFile.
   *
   * <p>Each path is relative to the VirtualFile and need not exist.
   *
   * @param callback will be run asynchronously sometime after the file changed.
   */
  public static @NotNull FileWatch subscribe(@NotNull VirtualFile base, @NotNull Iterable<String> paths, @NotNull Runnable callback) {
    final ImmutableSet.Builder<Location> builder = ImmutableSet.builder();
    for (String path : paths) {
      builder.add(new Location(base, path));
    }
    final FileWatch watcher =  new FileWatch(builder.build(), callback);
    subscriptions.subscribe(watcher);
    return watcher;
  }

  /**
   * Returns true if the given file matches this watch.
   */
  public boolean matches(VirtualFile file) {
    for (Location loc : watched) {
      if (loc.matches(file)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Unsubscribes this FileWatch from events.
   */
  public void unsubscribe() {
    if (!unsubscribed.compareAndSet(false, true)) {
      return; // already unsubscribed
    }
    subscriptions.unsubscribe(this);

    // Remove from dispose tree. Calls unsubscribe() again, harmlessly.
    Disposer.dispose(disposeLeaf);
  }

  /**
   * Automatically unsubscribes when a parent object is disposed.
   *
   * <p>Only one parent can be registered at a time. Auto-unsubscribe
   * will stop working for any object previously passed to this
   * method.
   */
  public void setDisposeParent(@NotNull Disposable parent) {
    if (unsubscribed.get()) return;
    Disposer.register(parent, disposeLeaf);
  }

  private void fireEvent() {
    if (unsubscribed.get()) return;

    try {
      callback.run();
    } catch (Exception e) {
      LOG.error("Uncaught exception in FileWatch callback", e);
      unsubscribe(); // avoid further errors
    }
  }

  /**
   * The location of a file or directory being watched.
   *
   * Since it might not exist yet, this consists of a base VirtualFile and a path to where it might appear.
   */
  private static class Location {
    private final @NotNull VirtualFile base;
    private final @Nullable String path;

    /**
     * The segments in the watched path, in reverse order (from leaf to base).
     */
    private final @NotNull List<String> reversedNames;

    Location(@NotNull VirtualFile base, @Nullable String path) {
      if (path != null && path.isEmpty()) {
        throw new IllegalArgumentException("can't watch an empty path");
      }
      this.base = base;
      this.path = path;
      this.reversedNames = path == null ? ImmutableList.of() : ImmutableList.copyOf(splitter.splitToList(path)).reverse();
    }

    /**
     * Returns the name at the end of the path being watched.
     */
    String getName() {
      if (reversedNames.isEmpty()) {
        return base.getName();
      } else {
        return reversedNames.get(0);
      }
    }

    /**
     * Returns true if the given VirtualFile is at this location.
     */
    boolean matches(VirtualFile file) {
      for (String name : reversedNames) {
        if (file == null || !file.getName().equals(name)) {
          return false;
        }
        file = file.getParent();
      }
      return base.equals(file);
    }

    @Override
    public boolean equals(Object obj) {
      return super.equals(obj);
    }

    private static final Splitter splitter = Splitter.on('/');
  }

  private static final Subscriptions subscriptions = new Subscriptions();

  private static class Subscriptions {

    /**
     * For each VirtualFile name, the FileWatches that are subscribed (across all Projects).
     *
     * <p>For thread safety, all access should be synchronized.
     */
    private final Multimap<String, FileWatch> byFile = LinkedListMultimap.create();

    private final Delivery delivery = new Delivery();

    synchronized void subscribe(FileWatch w) {
      for (Location loc : w.watched) {
        byFile.put(loc.getName(), w);
      }
      delivery.enable(!byFile.isEmpty());
    }

    synchronized void unsubscribe(FileWatch w) {
      for (Location loc : w.watched) {
        byFile.remove(loc.getName(), w);
      }
      delivery.enable(!byFile.isEmpty());
    }

    synchronized void addWatchesForFile(Set<FileWatch> out, VirtualFile f) {
      for (FileWatch w : byFile.get(f.getName())) {
        if (w.matches(f)) {
          out.add(w);
        }
      }
    }
  }

  private static class Delivery implements BulkFileListener {

    /**
     * The shared connection to IDEA's event system.
     *
     * <p>This will be non-null when there are one or more subscriptions.
     *
     * <p>For thread safety, all access should be synchronized.
     */
    private @Nullable MessageBusConnection bus;

    void enable(boolean enabled) {
      if (enabled) {
        if (bus == null) {
          final Application app = ApplicationManager.getApplication();
          bus = app.getMessageBus().connect();
          bus.subscribe(VirtualFileManager.VFS_CHANGES, this);
        }
      } else {
        if (bus != null) {
          bus.disconnect();
          bus = null;
        }
      }
    }

    @Override
    public void before(@NotNull List<? extends VFileEvent> events) {}

    @Override
    public void after(@NotNull List<? extends VFileEvent> events) {
      final Set<FileWatch> todo = new LinkedHashSet<>();
      synchronized (subscriptions) {
        for (VFileEvent event : events) {
          subscriptions.addWatchesForFile(todo, event.getFile());
        }
      }

      // Deliver changes synchronously, but after releasing the lock in case
      // the callback subscribes/unsubscribes.
      for (FileWatch w : todo) {
        w.fireEvent();
      }
    }
  }

  private static final Logger LOG = Logger.getInstance(FileWatch.class);
}
