/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.filters.UrlFilter;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.ide.runner.DartRelativePathsConsoleFilter;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import io.flutter.bazel.Workspace;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.run.common.ConsoleProps;
import io.flutter.run.common.RunMode;
import io.flutter.run.daemon.DaemonConsoleView;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Bazel version of the {@link io.flutter.run.test.TestLaunchState}.
 */
@SuppressWarnings("JavadocReference")
public class BazelTestLaunchState extends CommandLineState {
  @NotNull
  private final BazelTestConfig config;

  @NotNull
  private final BazelTestFields fields;

  @NotNull
  private final VirtualFile testFile;

  protected BazelTestLaunchState(ExecutionEnvironment env, @NotNull BazelTestConfig config, @Nullable VirtualFile testFile) {
    super(env);
    this.config = config;
    this.fields = config.getFields();
    if (testFile == null) {
      Workspace workspace = WorkspaceCache.getInstance(env.getProject()).get();
      assert(workspace != null);
      testFile = workspace.getRoot();
    }
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

    final BazelTestLaunchState launcher = new BazelTestLaunchState(env, config, virtualFile);
    final Workspace workspace = FlutterModuleUtils.getFlutterBazelWorkspace(env.getProject());
    if (workspace != null) {
      DaemonConsoleView.install(launcher, env, workspace.getRoot());
    }
    return launcher;
  }

  @Nullable
  @Override
  protected ConsoleView createConsole(@NotNull Executor executor) throws ExecutionException {
    // If the --machine output flag is not turned on, then don't activate the new window.
    if (fields.getAdditionalArgs() != null && fields.getAdditionalArgs().contains(BazelTestFields.Flags.noMachine)) {
      return super.createConsole(executor);
    }

    // Create a console showing a test tree.
    final Project project = getEnvironment().getProject();
    final Workspace workspace = WorkspaceCache.getInstance(project).get();

    // Fail gracefully if we have an unexpected null.
    if (workspace == null) {
      return super.createConsole(executor);
    }

    final DartUrlResolver resolver = DartUrlResolver.getInstance(project, workspace.getRoot());
    final ConsoleProps props = ConsoleProps.forBazel(config, executor, resolver);
    final BaseTestsOutputConsoleView console = SMTestRunnerConnectionUtil.createConsole(ConsoleProps.bazelFrameworkName, props);

    final String baseDir = workspace.getRoot().getPath();
    console.addMessageFilter(new DartRelativePathsConsoleFilter(project, baseDir));
    console.addMessageFilter(new UrlFilter());
    return console;
  }
}
