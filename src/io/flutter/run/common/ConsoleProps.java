/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.common;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.SMCustomMessagesParsing;
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.execution.ui.ConsoleView;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import io.flutter.run.bazelTest.BazelTestConfig;
import io.flutter.run.test.FlutterTestEventsConverter;
import io.flutter.run.test.FlutterTestLocationProvider;
import io.flutter.run.test.TestConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Configuration for the test console.
 * <p>
 * In particular, configures how it parses test events and handles the re-run action.
 */
public class ConsoleProps extends SMTRunnerConsoleProperties implements SMCustomMessagesParsing {
  /**
   * Name of the {@code testFrameworkName}passed to the {@link ConsoleProps} constructor for Pub-based consoles.
   */
  public static String pubFrameworkName = "FlutterTestRunner";

  /**
   * Name of the {@code testFrameworkName}passed to the {@link ConsoleProps} constructor for Bazel-based consoles..
   */
  public static String bazelFrameworkName = "FlutterBazelTestRunner";

  @NotNull
  private final DartUrlResolver resolver;

  private ConsoleProps(@NotNull RunConfiguration config,
                       @NotNull Executor exec,
                       @NotNull DartUrlResolver resolver,
                       String testFrameworkName) {
    super(config, "FlutterTestRunner", exec);
    this.resolver = resolver;
    setUsePredefinedMessageFilter(false);
    setIdBasedTestTree(true);
  }

  public static ConsoleProps forPub(@NotNull TestConfig config, @NotNull Executor exec, @NotNull DartUrlResolver resolver) {
    return new ConsoleProps(config, exec, resolver, pubFrameworkName);
  }

  public static ConsoleProps forBazel(@NotNull BazelTestConfig config, @NotNull Executor exec, @NotNull DartUrlResolver resolver) {
    return new ConsoleProps(config, exec, resolver, bazelFrameworkName);
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
    // TODO(github.com/flutter/flutter-intellij/issues/3504): Implement this.
    return null;
  }
}
