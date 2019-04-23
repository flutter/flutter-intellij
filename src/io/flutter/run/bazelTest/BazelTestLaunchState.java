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
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.SMCustomMessagesParsing;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ObservableConsoleView;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.ide.runner.DartRelativePathsConsoleFilter;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import io.flutter.bazel.Workspace;
import io.flutter.run.daemon.DaemonConsoleView;
import io.flutter.run.daemon.RunMode;
import io.flutter.run.test.FlutterTestEventsConverter;
import io.flutter.run.test.FlutterTestLocationProvider;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

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

  @Nullable
  @Override
  protected ConsoleView createConsole(@NotNull Executor executor) throws ExecutionException {
    // If the --machine output flag is not turned on, then don't activate the new window.
    if (fields.getAdditionalArgs() == null || !fields.getAdditionalArgs().contains("--machine")) {
      return super.createConsole(executor);
    }

    // Create a console showing a test tree.
    final Project project = getEnvironment().getProject();
    final Workspace workspace = Workspace.load(project);
    assert (workspace != null);
    final DartUrlResolver resolver = DartUrlResolver.getInstance(project, workspace.getRoot());
    final ConsoleProps props = new ConsoleProps(config, executor, resolver);
    final BaseTestsOutputConsoleView console = SMTestRunnerConnectionUtil.createConsole("FlutterBazelTestRunner", props);

    final String baseDir = workspace.getRoot().getPath();
    console.addMessageFilter(new DartRelativePathsConsoleFilter(project, baseDir));
    console.addMessageFilter(new UrlFilter());
    return console;
  }

  /**
   * Configuration for the test console.
   * <p>
   * In particular, configures how it parses test events and handles the re-run action.
   */
  private static class ConsoleProps extends SMTRunnerConsoleProperties implements SMCustomMessagesParsing {
    @NotNull
    private final DartUrlResolver resolver;

    public ConsoleProps(@NotNull BazelTestConfig config, @NotNull Executor exec, @NotNull DartUrlResolver resolver) {
      super(config, "FlutterBazelTestRunner", exec);
      this.resolver = resolver;
      setUsePredefinedMessageFilter(false);
      setIdBasedTestTree(true);
    }

    @Nullable
    @Override
    public SMTestLocator getTestLocator() {
      return FlutterTestLocationProvider.INSTANCE;
    }

    @Override
    public OutputToGeneralTestEventsConverter createTestEventsConverter(@NotNull String testFrameworkName,
                                                                        @NotNull TestConsoleProperties props) {
      return new FlutterTestEventsConverter(testFrameworkName, props, resolver);
    }

    @Nullable
    @Override
    public AbstractRerunFailedTestsAction createRerunFailedTestsAction(ConsoleView consoleView) {
      return null; // TODO(skybrian) implement
    }
  }
}
