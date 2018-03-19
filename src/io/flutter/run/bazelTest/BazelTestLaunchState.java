/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.run.daemon.RunMode;
import org.jetbrains.annotations.NotNull;

/**
 * The Bazel version of the {@link io.flutter.run.test.TestLaunchState}.
 */
public class BazelTestLaunchState extends CommandLineState {
  @NotNull
  private final BazelTestFields fields;

  @NotNull
  private final VirtualFile testFile;

  protected BazelTestLaunchState(ExecutionEnvironment env, @NotNull BazelTestConfig config, @NotNull VirtualFile testFile) {
    super(env);
    this.fields = config.getFields();
    this.testFile = testFile;
  }

  @NotNull
  VirtualFile getTestFile() {
    return testFile;
  }

  @NotNull
  @Override
  protected ProcessHandler startProcess() throws ExecutionException {
    final RunMode mode = RunMode.fromEnv(getEnvironment());
    return fields.run(getEnvironment().getProject(), mode);
  }

  public static BazelTestLaunchState create(@NotNull ExecutionEnvironment env, @NotNull BazelTestConfig config) throws ExecutionException {
    final BazelTestFields fields = config.getFields();
    try {
      fields.checkRunnable(env.getProject());
    }
    catch (RuntimeConfigurationError e) {
      throw new ExecutionException(e);
    }

    final VirtualFile virtualFile = fields.getFile();
    assert (virtualFile != null);

    return new BazelTestLaunchState(env, config, virtualFile);
    // TODO(jwren) do we want to use the DaemonConsoleView?
    //DaemonConsoleView.install(launcher, env, pubRoot.getRoot());
    //return launcher;
  }
}
