/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ColoredProcessHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterCommandStartResult {
  @NotNull
  public final FlutterCommandStartResultStatus status;

  @Nullable
  public final ColoredProcessHandler processHandler;

  @Nullable
  public final ExecutionException exception;

  public FlutterCommandStartResult(@NotNull FlutterCommandStartResultStatus status) {
    this(status, null, null);
  }

  public FlutterCommandStartResult(@Nullable ColoredProcessHandler processHandler) {
    this(FlutterCommandStartResultStatus.OK, processHandler, null);
  }

  public FlutterCommandStartResult(@Nullable ExecutionException exception) {
    this(FlutterCommandStartResultStatus.EXCEPTION, null, exception);
  }

  public FlutterCommandStartResult(@NotNull FlutterCommandStartResultStatus status,
                                   @Nullable ColoredProcessHandler processHandler,
                                   @Nullable ExecutionException exception) {
    this.status = status;
    this.processHandler = processHandler;
    this.exception = exception;
  }
}
