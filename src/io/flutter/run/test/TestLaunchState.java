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
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.SMCustomMessagesParsing;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.ide.runner.test.DartTestEventsConverter;
import com.jetbrains.lang.dart.ide.runner.util.DartTestLocationProvider;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import io.flutter.pub.PubRoot;
import io.flutter.run.daemon.DaemonConsoleView;
import io.flutter.run.daemon.RunMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A launcher that starts a process to run flutter tests, created from a run configuration.
 */
class TestLaunchState extends CommandLineState  {
  @NotNull
  private final TestConfig config;

  @NotNull
  private final TestFields fields;

  @NotNull
  private final VirtualFile testFileOrDir;

  @NotNull
  private final PubRoot pubRoot;

  private TestLaunchState(@NotNull ExecutionEnvironment env, @NotNull TestConfig config, @NotNull VirtualFile testFileOrDir,
                         @NotNull PubRoot pubRoot) {
    super(env);
    this.config = config;
    this.fields = config.getFields();
    this.testFileOrDir = testFileOrDir;
    this.pubRoot = pubRoot;
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
    assert(fileOrDir != null);

    final PubRoot pubRoot = fields.getPubRoot(env.getProject());
    assert(pubRoot != null);

    final TestLaunchState launcher = new TestLaunchState(env, config, fileOrDir, pubRoot);
    DaemonConsoleView.install(launcher, env, pubRoot.getRoot());
    return launcher;
  }

  @NotNull
  @Override
  protected ProcessHandler startProcess() throws ExecutionException {
    final RunMode mode = RunMode.fromEnv(getEnvironment());
    return fields.run(getEnvironment().getProject(), mode);
  }

  @Nullable
  @Override
  protected ConsoleView createConsole(@NotNull Executor executor) throws ExecutionException {
    // Create the UI showing the test tree.
    final DartUrlResolver resolver = DartUrlResolver.getInstance(getEnvironment().getProject(), testFileOrDir);
    final ConsoleProps props = new ConsoleProps(config, executor, resolver);
    return SMTestRunnerConnectionUtil.createConsole("FlutterTestRunner", props);
  }

  @NotNull
  VirtualFile getTestFileOrDir() throws ExecutionException {
    return testFileOrDir;
  }

  @NotNull
  PubRoot getPubRoot() throws ExecutionException {
    return pubRoot;
  }

  /**
   * Configuration for the test console.
   * <p>
   * In particular, configures how it parses test events and handles the re-run action.
   */
  private static class ConsoleProps extends SMTRunnerConsoleProperties implements SMCustomMessagesParsing {
    @NotNull
    private final DartUrlResolver resolver;

    public ConsoleProps(@NotNull TestConfig config, @NotNull Executor exec, @NotNull DartUrlResolver resolver) {
      super(config, "FlutterTestRunner", exec);
      this.resolver = resolver;
      setUsePredefinedMessageFilter(false);
      setIdBasedTestTree(true);
    }

    @Nullable
    @Override
    public SMTestLocator getTestLocator() {
      return DartTestLocationProvider.INSTANCE;
    }

    @Override
    public OutputToGeneralTestEventsConverter createTestEventsConverter(@NotNull String testFrameworkName,
                                                                        @NotNull TestConsoleProperties props) {
      return new DartTestEventsConverter(testFrameworkName, props, resolver);
    }

    @Nullable
    @Override
    public AbstractRerunFailedTestsAction createRerunFailedTestsAction(ConsoleView consoleView) {
      return null; // TODO(skybrian) implement
    }
  }

}
