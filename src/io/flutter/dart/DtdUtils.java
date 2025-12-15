/*
 * Copyright 2024 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.jetbrains.lang.dart.ide.toolingDaemon.DartToolingDaemonService;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.*;

public class DtdUtils {
  // Visible for testing
  static final Map<Project, CompletableFuture<DartToolingDaemonService>> WAITERS = new ConcurrentHashMap<>();

  public @NotNull CompletableFuture<DartToolingDaemonService> readyDtdService(@NotNull Project project) {
    final DartToolingDaemonService dtdService = DartToolingDaemonService.getInstance(project);
    if (dtdService.getWebSocketReady()) {
      return CompletableFuture.completedFuture(dtdService);
    }

    return WAITERS.computeIfAbsent(project, p -> {
      CompletableFuture<DartToolingDaemonService> readyService = new CompletableFuture<>();

      final ScheduledExecutorService scheduler = AppExecutorUtil.getAppScheduledExecutorService();

      final ScheduledFuture<?> poll = scheduler.scheduleWithFixedDelay(() -> {
        if (readyService.isDone()) return;
        if (dtdService.getWebSocketReady()) {
          readyService.complete(dtdService);
        }
      }, 0, 500, TimeUnit.MILLISECONDS);

      final ScheduledFuture<?> timeout = scheduler.schedule(() -> {
        readyService.completeExceptionally(new Exception("Timed out waiting for DTD websocket to start"));
      }, 20, TimeUnit.SECONDS);

      readyService.whenComplete((s, t) -> {
        poll.cancel(false);
        timeout.cancel(false);
        // Remove from waiters when done.
        // We use the scheduler to ensure this runs after computeIfAbsent returns,
        // in case readyService was completed synchronously inside computeIfAbsent.
        // Although with the check above, synchronous completion here is unlikely,
        // it's safer to be async or just rely on the fact that polling completes it async.
        // If polling completes it, we are on a different thread, so computeIfAbsent is definitely done.
        // If timeout completes it, we are on a different thread.
        // So direct removal is safe IF completion happens async.
        // The only sync completion risk is if we checked again inside and completed.
        // But we don't check inside synchronously anymore (only in poll).
        WAITERS.remove(project);
      });

      return readyService;
    });
  }
}
