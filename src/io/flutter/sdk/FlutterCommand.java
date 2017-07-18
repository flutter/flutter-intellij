/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.google.common.collect.ImmutableList;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterBundle;
import io.flutter.FlutterInitializer;
import io.flutter.FlutterMessages;
import io.flutter.android.IntelliJAndroidSdk;
import io.flutter.console.FlutterConsoles;
import io.flutter.dart.DartPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A Flutter command to run, with its arguments.
 */
public class FlutterCommand {
  private static final Logger LOG = Logger.getInstance(FlutterCommand.class);

  private static final Set<Type> pubRelatedCommands = new HashSet<>(
    Arrays.asList(Type.PACKAGES_GET, Type.PACKAGES_UPGRADE, Type.UPGRADE));

  @NotNull
  private final FlutterSdk sdk;

  @NotNull
  private final VirtualFile workDir;

  @NotNull
  private final Type type;

  @NotNull
  private final List<String> args;

  /**
   * @see FlutterSdk for methods to create specific commands.
   */
  FlutterCommand(@NotNull FlutterSdk sdk, @NotNull VirtualFile workDir, @NotNull Type type, String... args) {
    this.sdk = sdk;
    this.workDir = workDir;
    this.type = type;
    this.args = ImmutableList.copyOf(args);
  }

  /**
   * Returns a displayable version of the command that will be run.
   */
  public String getDisplayCommand() {
    final List<String> words = new ArrayList<>();
    words.add("flutter");
    words.addAll(type.subCommand);
    words.addAll(args);
    return String.join(" ", words);
  }

  protected boolean isPubRelatedCommand() {
    return pubRelatedCommands.contains(type);
  }

  /**
   * Starts running the command, without showing its output in a console.
   * <p>
   * If unable to start (for example, if a command is already running), returns null.
   */
  public Process start(@Nullable Consumer<ProcessOutput> onDone, @Nullable ProcessListener processListener) {
    // TODO(skybrian) add Project parameter if it turns out later that we need to set ANDROID_HOME.
    final OSProcessHandler handler = startProcess(null);
    if (handler == null) {
      return null;
    }

    if (processListener != null) {
      handler.addProcessListener(processListener);
    }

    // Capture all process output if requested.
    if (onDone != null) {
      final CapturingProcessAdapter listener = new CapturingProcessAdapter() {
        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
          super.processTerminated(event);
          onDone.accept(getOutput());
        }
      };
      handler.addProcessListener(listener);
    }

    // Transition to "running" state.
    handler.startNotify();

    return handler.getProcess();
  }

  /**
   * Starts running the command, showing its output in a non-module console.
   * <p>
   * Shows the output in a tab in the tool window that's not associated
   * with a particular module. Returns the process.
   * <p>
   * If unable to start (for example, if a command is already running), returns null.
   */
  public Process startInConsole(@NotNull Project project) {
    final OSProcessHandler handler = startProcess(project);
    if (handler == null) {
      return null;
    }
    FlutterConsoles.displayProcessLater(handler, project, null, handler::startNotify);
    return handler.getProcess();
  }

  /**
   * Starts running the command, showing its output in a module console.
   * <p>
   * Shows the output in the tool window's tab corresponding to the passed-in module.
   * Returns the process.
   * <p>
   * If unable to start (for example, if a command is already running), returns null.
   */
  public Process startInModuleConsole(@NotNull Module module, @Nullable Runnable onDone, @Nullable ProcessListener processListener) {
    final OSProcessHandler handler = startProcess(module.getProject());
    if (handler == null) {
      return null;
    }
    if (processListener != null) {
      handler.addProcessListener(processListener);
    }
    handler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(ProcessEvent event) {
        if (onDone != null) {
          onDone.run();
        }
      }
    });

    FlutterConsoles.displayProcessLater(handler, module.getProject(), module, handler::startNotify);
    return handler.getProcess();
  }

  @Override
  public String toString() {
    return "FlutterCommand(" + getDisplayCommand() + ")";
  }

  /**
   * The currently running command.
   * <p>
   * We only allow one command to run at a time across all IDEA projects.
   */
  private static final AtomicReference<FlutterCommand> inProgress = new AtomicReference<>(null);

  /**
   * Starts a process that runs a flutter command, unless one is already running.
   * <p>
   * Returns the handler if successfully started.
   */
  public OSProcessHandler startProcess(boolean sendAnalytics) {
    try {
      final GeneralCommandLine commandLine = createGeneralCommandLine(null);
      LOG.info(commandLine.toString());
      final OSProcessHandler handler = new OSProcessHandler(commandLine);
      if (sendAnalytics) {
        type.sendAnalyticsEvent();
      }
      return handler;
    }
    catch (ExecutionException e) {
      FlutterMessages.showError(
        type.title,
        FlutterBundle.message("flutter.command.exception.message", e.getMessage()));
      return null;
    }
  }

  /**
   * Starts a process that runs a flutter command, unless one is already running.
   * <p>
   * If a project is supplied, it will be used to determine the ANDROID_HOME variable for the subprocess.
   * <p>
   * Returns the handler if successfully started.
   */
  @Nullable
  public OSProcessHandler startProcess(@Nullable Project project) {
    // TODO(devoncarew): Many flutter commands can legitimately be run in parallel.
    if (!inProgress.compareAndSet(null, this)) {
      return null;
    }

    if (isPubRelatedCommand()) {
      DartPlugin.setPubActionInProgress(true);
    }

    final OSProcessHandler handler;
    try {
      final GeneralCommandLine commandLine = createGeneralCommandLine(project);
      LOG.info(commandLine.toString());
      handler = new OSProcessHandler(commandLine);
      handler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(final ProcessEvent event) {
          inProgress.compareAndSet(FlutterCommand.this, null);
          if (isPubRelatedCommand()) {
            DartPlugin.setPubActionInProgress(false);
          }
        }
      });
      type.sendAnalyticsEvent();
      return handler;
    }
    catch (ExecutionException e) {
      inProgress.compareAndSet(this, null);
      if (isPubRelatedCommand()) {
        DartPlugin.setPubActionInProgress(false);
      }
      FlutterMessages.showError(
        type.title,
        FlutterBundle.message("flutter.command.exception.message", e.getMessage()));
      return null;
    }
  }

  /**
   * Creates the command line to run.
   * <p>
   * If a project is supplied, it will be used to determine the ANDROID_HOME variable for the subprocess.
   */
  @NotNull
  public GeneralCommandLine createGeneralCommandLine(@Nullable Project project) {
    final GeneralCommandLine line = new GeneralCommandLine();
    line.setCharset(CharsetToolkit.UTF8_CHARSET);

    line.withEnvironment(FlutterSdkUtil.FLUTTER_HOST_ENV, FlutterSdkUtil.getFlutterHostEnvValue());

    final String androidHome = IntelliJAndroidSdk.chooseAndroidHome(project, false);
    if (androidHome != null) {
      line.withEnvironment("ANDROID_HOME", androidHome);
    }

    line.setExePath(FileUtil.toSystemDependentName(sdk.getHomePath() + "/bin/" + FlutterSdkUtil.flutterScriptName()));
    line.setWorkDirectory(workDir.getPath());
    line.addParameter("--no-color");
    line.addParameters(type.subCommand);
    line.addParameters(args);
    return line;
  }

  enum Type {
    CONFIG("Flutter config", "config"),
    CREATE("Flutter create", "create"),
    DOCTOR("Flutter doctor", "doctor"),
    PACKAGES_GET("Flutter packages get", "packages", "get"),
    PACKAGES_UPGRADE("Flutter packages upgrade", "packages", "upgrade"),
    UPGRADE("Flutter upgrade", "upgrade"),
    VERSION("Flutter version", "--version"),
    RUN("Flutter run", "run"),
    TEST("Flutter test", "test");

    final public String title;
    final ImmutableList<String> subCommand;

    Type(String title, String... subCommand) {
      this.title = title;
      this.subCommand = ImmutableList.copyOf(subCommand);
    }

    void sendAnalyticsEvent() {
      final String action = String.join("_", subCommand).replaceAll("-", "");
      FlutterInitializer.getAnalytics().sendEvent("flutter", action);
    }
  }
}
