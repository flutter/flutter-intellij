/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import io.flutter.FlutterUtils;
import io.flutter.android.AndroidEmulator;
import io.flutter.android.AndroidSdk;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class manages the list of known Android eumlators, and handles refreshing the list as well
 * as notifying interested parties when the list changes.
 */
public class AndroidEmulatorManager {
  private static final Logger LOG = Logger.getInstance(AndroidEmulatorManager.class);

  @NotNull
  public static AndroidEmulatorManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, AndroidEmulatorManager.class);
  }

  private final @NotNull Project project;
  private final AtomicReference<ImmutableSet<Runnable>> listeners = new AtomicReference<>(ImmutableSet.of());

  private List<AndroidEmulator> cachedEmulators = new ArrayList<>();

  private AndroidEmulatorManager(@NotNull Project project) {
    this.project = project;
  }

  public void addListener(@NotNull Runnable callback) {
    listeners.updateAndGet((old) -> {
      final List<Runnable> changed = new ArrayList<>(old);
      changed.add(callback);
      return ImmutableSet.copyOf(changed);
    });
  }

  private CompletableFuture<List<AndroidEmulator>> inProgressRefresh;

  public CompletableFuture<List<AndroidEmulator>> refresh() {
    // We don't need to refresh if one is in progress.
    synchronized (this) {
      if (inProgressRefresh != null) {
        return inProgressRefresh;
      }
    }

    final CompletableFuture<List<AndroidEmulator>> future = new CompletableFuture<>();

    synchronized (this) {
      inProgressRefresh = future;
    }

    AppExecutorUtil.getAppExecutorService().submit(() -> {
      final AndroidSdk sdk = AndroidSdk.createFromProject(project);
      if (sdk == null) {
        future.complete(Collections.emptyList());
      }
      else {
        final List<AndroidEmulator> emulators = sdk.getEmulators();
        emulators.sort((emulator1, emulator2) -> emulator1.getName().compareToIgnoreCase(emulator2.getName()));
        future.complete(emulators);
      }
    });

    future.thenAccept(emulators -> {
      fireChangeEvent(emulators, cachedEmulators);

      synchronized (this) {
        inProgressRefresh = null;
      }
    });

    return future;
  }

  public List<AndroidEmulator> getCachedEmulators() {
    return cachedEmulators;
  }

  private void fireChangeEvent(final List<AndroidEmulator> newEmulators, final List<AndroidEmulator> oldEmulators) {
    if (project.isDisposed()) return;

    // Don't fire if the list of devices is unchanged.
    if (cachedEmulators.equals(newEmulators)) {
      return;
    }

    cachedEmulators = newEmulators;

    for (Runnable listener : listeners.get()) {
      try {
        listener.run();
      }
      catch (Exception e) {
        FlutterUtils.warn(LOG, "AndroidEmulatorManager listener threw an exception", e);
      }
    }
  }
}
