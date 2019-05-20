/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.test;

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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.ide.runner.DartRelativePathsConsoleFilter;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import io.flutter.FlutterBundle;
import io.flutter.console.FlutterConsoleFilter;
import io.flutter.pub.PubRoot;
import io.flutter.run.common.ConsoleProps;
import io.flutter.run.daemon.DaemonConsoleView;
import io.flutter.run.daemon.RunMode;
import io.flutter.sdk.FlutterCommandStartResult;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A launcher that starts a process to run flutter tests, created from a run configuration.
 */
class TestLaunchState extends CommandLineState {
  @NotNull
  private final TestConfig config;

  @NotNull
  private final TestFields fields;

  @NotNull
  private final VirtualFile testFileOrDir;

  @NotNull
  private final PubRoot pubRoot;

  private final boolean testConsoleEnabled;

  private TestLaunchState(@NotNull ExecutionEnvironment env, @NotNull TestConfig config, @NotNull VirtualFile testFileOrDir,
                          @NotNull PubRoot pubRoot, boolean testConsoleEnabled) {
    super(env);
    this.config = config;
    this.fields = config.getFields();
    this.testFileOrDir = testFileOrDir;
    this.pubRoot = pubRoot;
    this.testConsoleEnabled = testConsoleEnabled;
  }

  static TestLaunchState create(@NotNull ExecutionEnvironment env, @NotNull TestConfig config) throws ExecutionException {
    final TestFields fields = config.getFields();
    try {
      fields.checkRunnable(env.getProject());
    }
    catch (RuntimeConfigurationError e) {
      throw new ExecutionException(e);
    }

    final VirtualFile fileOrDir = fields.getFileOrDir();
    assert (fileOrDir != null);

    final PubRoot pubRoot = fields.getPubRoot(env.getProject());
    assert (pubRoot != null);

    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(env.getProject());
    assert (sdk != null);
    final boolean testConsoleEnabled = sdk.getVersion().flutterTestSupportsMachineMode();

    final TestLaunchState launcher = new TestLaunchState(env, config, fileOrDir, pubRoot, testConsoleEnabled);
    DaemonConsoleView.install(launcher, env, pubRoot.getRoot());
    return launcher;
  }

  @NotNull
  @Override
  protected ProcessHandler startProcess() throws ExecutionException {
    final RunMode mode = RunMode.fromEnv(getEnvironment());
    final FlutterCommandStartResult result = fields.run(getEnvironment().getProject(), mode);
    switch (result.status) {
      case OK:
        assert result.processHandler != null;
        return result.processHandler;
      case EXCEPTION:
        assert result.exception != null;
        throw new ExecutionException(FlutterBundle.message("flutter.command.exception.message" + result.exception.getMessage()));
      default:
        throw new ExecutionException("Unexpected state");
    }
  }

  @Nullable
  @Override
  protected ConsoleView createConsole(@NotNull Executor executor) throws ExecutionException {
    if (!testConsoleEnabled) {
      return super.createConsole(executor);
    }

    // Create a console showing a test tree.
    final Project project = getEnvironment().getProject();
    final DartUrlResolver resolver = DartUrlResolver.getInstance(project, testFileOrDir);
    final ConsoleProps props = ConsoleProps.forPub(config, executor, resolver);
    final BaseTestsOutputConsoleView console = SMTestRunnerConnectionUtil.createConsole(ConsoleProps.pubFrameworkName, props);
    final Module module = ModuleUtil.findModuleForFile(testFileOrDir, project);
    if (module != null) {
      console.addMessageFilter(new FlutterConsoleFilter(module));
    }
    final String baseDir = getBaseDir();
    if (baseDir != null) {
      console.addMessageFilter(new DartRelativePathsConsoleFilter(project, baseDir));
    }
    console.addMessageFilter(new UrlFilter());
    return console;
  }

  @Nullable
  private String getBaseDir() {
    final PubRoot root = config.getFields().getPubRoot(config.getProject());
    if (root != null) {
      return root.getPath();
    }
    final VirtualFile baseDir = config.getProject().getBaseDir();
    return baseDir == null ? null : baseDir.getPath();
  }

  @NotNull
  VirtualFile getTestFileOrDir() {
    return testFileOrDir;
  }

  @NotNull
  PubRoot getPubRoot() {
    return pubRoot;
  }
}
