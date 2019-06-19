/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;

/**
 * An {@link OSProcessHandler} that uses {@code BaseOutputReader.Options.forMostlySilentProcess()}
 * in order to reduce cpu usage of the process it runs.
 *
 * <p>
 * This also addresses a log message in the IntelliJ log.
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
