/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.OSProcessHandler;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class FlutterCommandStartResult {
  @NotNull
  public final FlutterCommandStartResultStatus status;

  @Nullable
  public final OSProcessHandler processHandler;

  @Nullable
  public final ExecutionException exception;

  public FlutterCommandStartResult(@NotNull FlutterCommandStartResultStatus status,
                                   @Nullable OSProcessHandler processHandler,
                                   @Nullable ExecutionException exception) {
    this.status = status;
    this.processHandler = processHandler;
    this.exception = exception;
  }
}
