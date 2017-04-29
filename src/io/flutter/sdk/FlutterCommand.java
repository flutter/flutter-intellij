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
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterBundle;
import io.flutter.FlutterInitializer;
import io.flutter.FlutterMessages;
import io.flutter.console.FlutterConsoles;
import io.flutter.dart.DartPlugin;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A Flutter command to run, with its arguments.
 */
public class FlutterCommand {
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

  /**
   * Starts running the command, without showing its output in a console.
   * <p>
   * If unable to start (for example, if a command is already running), returns null.
   */
  public Process start(@Nullable Consumer<ProcessOutput> onDone) {
    final OSProcessHandler handler = startProcess();
    if (handler == null) {
      return null;
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
    final OSProcessHandler handler = startProcess();
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
  public Process startInModuleConsole(Module module, Runnable onDone) {
    final OSProcessHandler handler = startProcess();
    if (handler == null) {
      return null;
    }
    handler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(ProcessEvent event) {
        onDone.run();
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
  @Nullable
  private OSProcessHandler startProcess() {
    if (!inProgress.compareAndSet(null, this)) {
      return null;
    }
    DartPlugin.setPubActionInProgress(true);

    final OSProcessHandler handler;
    try {
      handler = new OSProcessHandler(createGeneralCommandLine());
      handler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(final ProcessEvent event) {
          inProgress.compareAndSet(FlutterCommand.this, null);
          DartPlugin.setPubActionInProgress(false);
        }
      });
      type.sendAnalyticsEvent();
      return handler;
    }
    catch (ExecutionException e) {
      inProgress.compareAndSet(this, null);
      DartPlugin.setPubActionInProgress(false);
      FlutterMessages.showError(
        type.title,
        FlutterBundle.message("flutter.command.exception.message", e.getMessage()));
      return null;
    }
  }

  @NotNull
  private GeneralCommandLine createGeneralCommandLine() {
    final GeneralCommandLine line = new GeneralCommandLine();
    line.setCharset(CharsetToolkit.UTF8_CHARSET);
    line.setExePath(sdk.getHomePath() + "/bin/" + FlutterSdkUtil.flutterScriptName());
    line.withEnvironment(FlutterSdkUtil.FLUTTER_HOST_ENV, FlutterSdkUtil.getFlutterHostEnvValue());
    line.setWorkDirectory(workDir.getPath());
    line.addParameter("--no-color");
    line.addParameters(type.subCommand);
    line.addParameters(args);
    return line;
  }

  enum Type {
    CREATE("Flutter create", "create"),
    DOCTOR("Flutter doctor", "doctor"),
    PACKAGES_GET("Flutter packages get", "packages", "get"),
    PACKAGES_UPGRADE("Flutter packages upgrade", "packages", "upgrade"),
    UPGRADE("Flutter upgrade", "upgrade"),
    VERSION("Flutter version", "--version");

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

  private static final Logger LOG = Logger.getInstance(FlutterApp.class);
}
