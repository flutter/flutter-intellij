/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.test;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.pub.PubRoot;
import io.flutter.run.daemon.DaemonConsoleView;
import io.flutter.run.daemon.RunMode;
import org.jetbrains.annotations.NotNull;

/**
 * A launcher that starts a process to run flutter tests, created from a run configuration.
 */
class TestLaunchState extends CommandLineState  {
  @NotNull
  private final TestFields fields;

  @NotNull
  private final VirtualFile testFileOrDir;

  @NotNull
  private final PubRoot pubRoot;

  private TestLaunchState(@NotNull ExecutionEnvironment env, @NotNull TestFields fields, @NotNull VirtualFile testFileOrDir,
                         @NotNull PubRoot pubRoot) {
    super(env);
    this.fields = fields;
    this.testFileOrDir = testFileOrDir;
    this.pubRoot = pubRoot;
  }

  static TestLaunchState create(@NotNull ExecutionEnvironment env, @NotNull TestFields fields) throws ExecutionException {
    try {
      fields.checkRunnable(env.getProject());
    }
    catch (RuntimeConfigurationError e) {
      throw new ExecutionException(e);
    }

    final VirtualFile fileOrDir = fields.getFileOrDir();
    assert(fileOrDir != null);

    final PubRoot pubRoot = fields.getPubRoot(env.getProject());
    assert(pubRoot != null);

    final TestLaunchState launcher = new TestLaunchState(env, fields, fileOrDir, pubRoot);
    DaemonConsoleView.install(launcher, env, pubRoot.getRoot());
    return launcher;
  }

  @NotNull
  @Override
  protected ProcessHandler startProcess() throws ExecutionException {
    final RunMode mode = RunMode.fromEnv(getEnvironment());
    return fields.run(getEnvironment().getProject(), mode);
  }

  @NotNull
  VirtualFile getTestFileOrDir() throws ExecutionException {
    return testFileOrDir;
  }

  @NotNull
  PubRoot getPubRoot() throws ExecutionException {
    return pubRoot;
  }
}
