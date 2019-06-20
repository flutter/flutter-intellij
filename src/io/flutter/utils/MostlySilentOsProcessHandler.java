/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;

/**
 * An {@link OSProcessHandler} that uses {@code BaseOutputReader.Options.forMostlySilentProcess}
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
 */
public class MostlySilentOsProcessHandler extends OSProcessHandler {
  public MostlySilentOsProcessHandler(@NotNull GeneralCommandLine commandLine)
    throws ExecutionException {
    super(commandLine);
  }

  @NotNull
  @Override
  protected BaseOutputReader.Options readerOptions() {
    return BaseOutputReader.Options.forMostlySilentProcess();
  }
}
