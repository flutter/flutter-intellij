/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.openapi.util.Key;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A {@link KillableColoredProcessHandler} that uses {@code BaseOutputReader.Options.forMostlySilentProcess}
 * in order to reduce cpu usage of the process it runs.
 *
 * <p>
 * This works by defaulting to a non-blocking process polling mode instead of a blocking mode.
 * The default can be overriden by setting the following registry flags:
 * <ul>
 *   <li> "output.reader.blocking.mode.for.mostly.silent.processes" = false
 *   <li> "output.reader.blocking.mode" = true
 * </ul>
 *
 * <p>
 * Note that long-running processes that don't use these options may log a warning in message
 * in the IntelliJ log.  See {@link BaseOSProcessHandler}'s {@code SimpleOutputReader.beforeSleeping}
 * for more information.
 *
 * <p>
 * Extends {@link KillableColoredProcessHandler} rather than {@code ColoredProcessHandler} with
 * manual {@code UnixProcessManager.sendSigIntToProcessTree} calls because {@code UnixProcessManager}
 * is {@code @ApiStatus.Internal} and {@code sendSigIntToProcessTree} is {@code @ScheduledForRemoval}
 * in {@code 262+}, whereas {@link KillableColoredProcessHandler} enables graceful {@code SIGINT} /
 * Ctrl+C termination across {@code 252}+ via public Platform API.
 */
public class MostlySilentColoredProcessHandler extends KillableColoredProcessHandler {
  private final @Nullable Consumer<String> onTextAvailable;

  public MostlySilentColoredProcessHandler(@NotNull GeneralCommandLine commandLine)
    throws ExecutionException {
    this(commandLine, null);
  }

  public MostlySilentColoredProcessHandler(
    @NotNull GeneralCommandLine commandLine,
    @Nullable Consumer<String> onTextAvailable
  ) throws ExecutionException {
    super(commandLine);
    this.onTextAvailable = onTextAvailable;
  }

  @NotNull
  @Override
  protected BaseOutputReader.Options readerOptions() {
    return BaseOutputReader.Options.forMostlySilentProcess();
  }

  @Override
  public void coloredTextAvailable(@NotNull String text, @NotNull Key outputType) {
    super.coloredTextAvailable(text, outputType);

    if (onTextAvailable != null) {
      onTextAvailable.accept(text);
    }
  }
}
