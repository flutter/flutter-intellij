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
  private static final Map<Project, CompletableFuture<DartToolingDaemonService>> WAITERS = new ConcurrentHashMap<>();

  public @NotNull CompletableFuture<DartToolingDaemonService> readyDtdService(@NotNull Project project) {
    return WAITERS.computeIfAbsent(project, p -> {
      final DartToolingDaemonService dtdService = DartToolingDaemonService.getInstance(project);
      CompletableFuture<DartToolingDaemonService> readyService = new CompletableFuture<>();

      if (dtdService.getWebSocketReady()) {
        readyService.complete(dtdService);
        return readyService;
      }

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
        if (t != null) {
          WAITERS.remove(p);
        }
      });

      return readyService;
    });
  }
}
