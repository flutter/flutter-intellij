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
import io.flutter.bazel.Workspace;
import io.flutter.run.daemon.DaemonConsoleView;
import io.flutter.run.daemon.RunMode;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Bazel version of the {@link io.flutter.run.test.TestLaunchState}.
 */
@SuppressWarnings("JavadocReference")
public class BazelTestLaunchState extends CommandLineState {
  @NotNull
  private final BazelTestFields fields;

  @NotNull
  private final VirtualFile testFile;

  protected BazelTestLaunchState(ExecutionEnvironment env, @NotNull BazelTestConfig config, @Nullable VirtualFile testFile) {
    super(env);
    this.fields = config.getFields();
    this.testFile = testFile == null
                    ? Workspace.load(env.getProject()).getRoot()
                    : testFile;
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

    final BazelTestLaunchState launcher = new BazelTestLaunchState(env, config, virtualFile);
    final Workspace workspace = FlutterModuleUtils.getFlutterBazelWorkspace(env.getProject());
    if (workspace != null) {
      DaemonConsoleView.install(launcher, env, workspace.getRoot());
    }
    return launcher;
  }
}
