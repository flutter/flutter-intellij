/*
 * Copyright 2024 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import com.intellij.openapi.project.Project;
import com.jetbrains.lang.dart.ide.toolingDaemon.DartToolingDaemonService;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class DtdUtils {
  public @NotNull CompletableFuture<DartToolingDaemonService> readyDtdService(@NotNull Project project) {
    final DartToolingDaemonService dtdService = DartToolingDaemonService.getInstance(project);
    CompletableFuture<DartToolingDaemonService> readyService = new CompletableFuture<>();
    int attemptsRemaining = 10;
    final int TIME_IN_BETWEEN = 2;
    while (attemptsRemaining > 0) {
      attemptsRemaining--;
      if (dtdService.getWebSocketReady()) {
        readyService.complete(dtdService);
        break;
      }
      try {
        Thread.sleep(TIME_IN_BETWEEN * 1000);
      }
      catch (InterruptedException e) {
        readyService.completeExceptionally(e);
        break;
      }
    }
    if (!readyService.isDone()) {
      readyService.completeExceptionally(new Exception("Timed out waiting for DTD websocket to start"));
    }
    return readyService;
  }
}
