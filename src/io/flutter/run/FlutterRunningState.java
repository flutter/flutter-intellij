/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.net.NetUtils;
import com.jetbrains.lang.dart.DartBundle;
import com.jetbrains.lang.dart.ide.runner.server.DartCommandLineRunningState;
import com.jetbrains.lang.dart.sdk.DartSdk;
import io.flutter.FlutterBundle;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterRunningState extends DartCommandLineRunningState {
  private int myObservatoryPort;

  public FlutterRunningState(@NotNull ExecutionEnvironment environment) throws ExecutionException {
    super(environment);
    //((TextConsoleBuilderImpl)getConsoleBuilder()).setUsePredefinedMessageFilter(true);
    getConsoleBuilder().addFilter((line, entireLength) -> {
      if (line.trim().equals("open -a Simulator.app")) {
        final int startOffset = entireLength - line.length();
        final int endOffset = startOffset + line.length();
        return new Filter.Result(entireLength - line.length(), endOffset, new OpenIOSSimulatorLink());
      }
      return null;
    });
  }

  public FlutterRunnerParameters params() {
    return (FlutterRunnerParameters)myRunnerParameters;
  }

  protected ProcessHandler doStartProcess(final @Nullable String overriddenMainFilePath) throws ExecutionException {
    final GeneralCommandLine commandLine = createCommandLine(overriddenMainFilePath);
    final OSProcessHandler processHandler = new KillableColoredProcessHandler(commandLine);
    ProcessTerminatedListener.attach(processHandler, getEnvironment().getProject());
    return processHandler;
  }

  GeneralCommandLine createCommandLine(final @Nullable String overriddenMainFilePath) throws ExecutionException {
    DartSdk sdk = DartSdk.getDartSdk(getEnvironment().getProject());
    if (sdk == null) {
      throw new ExecutionException(DartBundle.message("dart.sdk.is.not.configured"));
    }

    FlutterRunnerParameters params = (FlutterRunnerParameters)myRunnerParameters;
    FlutterSdk flutterSdk = FlutterSdk.getFlutterSdk(getEnvironment().getProject());
    if (flutterSdk == null) {
      throw new ExecutionException(FlutterBundle.message("flutter.sdk.is.not.configured"));
    }

    String flutterSdkPath = flutterSdk.getHomePath();
    VirtualFile projectDir = flutterProjectDir(params.getFilePath());

    String workingDir = projectDir.getCanonicalPath();
    String flutterExec = FlutterSdkUtil.pathToFlutterTool(flutterSdkPath);

    final GeneralCommandLine commandLine = new GeneralCommandLine().withWorkDirectory(workingDir);
    commandLine.setCharset(CharsetToolkit.UTF8_CHARSET);
    commandLine.setExePath(FileUtil.toSystemDependentName(flutterExec));
    commandLine.getEnvironment().putAll(myRunnerParameters.getEnvs());
    commandLine.withParentEnvironmentType(myRunnerParameters.isIncludeParentEnvs()
                                          ? GeneralCommandLine.ParentEnvironmentType.CONSOLE
                                          : GeneralCommandLine.ParentEnvironmentType.NONE);
    commandLine.addParameter(startUpCommand());
    if ("Debug".equals(getEnvironment().getExecutor().getActionName())) {
      myObservatoryPort = NetUtils.tryToFindAvailableSocketPort();
      if (myObservatoryPort < 0) {
        throw new ExecutionException(FlutterBundle.message("no.socket.for.debugging"));
      }
      if (startUpCommand().equals("run")) {
        commandLine.addParameter("--start-paused");
        commandLine.addParameter("--debug-port");
        commandLine.addParameter(String.valueOf(myObservatoryPort));
      }
    }

    return commandLine;
  }

  String startUpCommand() {
    return "run";
  }

  @NotNull
  public static VirtualFile flutterProjectDir(@Nullable String projectPath) throws ExecutionException {
    if (projectPath == null) throw new ExecutionException(FlutterBundle.message("no.project.dir"));
    VirtualFile projectDir = LocalFileSystem.getInstance().findFileByPath(projectPath);
    while (projectDir != null) {
      if (projectDir.isDirectory() && projectDir.findChild("pubspec.yaml") != null) {
        return projectDir;
      }
      projectDir = projectDir.getParent();
    }
    throw new ExecutionException(FlutterBundle.message("no.project.dir"));
  }

  public int getObservatoryPort() {
    return myObservatoryPort;
  }

  private class OpenIOSSimulatorLink implements HyperlinkInfo {
    @Override
    public void navigate(Project project) {
      final GeneralCommandLine cmd = new GeneralCommandLine().withExePath("open").withParameters("-a", "Simulator.app");
      try {
        final OSProcessHandler handler = new OSProcessHandler(cmd);
        handler.addProcessListener(new ProcessAdapter() {
          @Override
          public void processTerminated(final ProcessEvent event) {
            //TODO: open a toast (https://github.com/flutter/flutter-intellij/issues/127).
          }
        });
        handler.startNotify();
      } catch (ExecutionException e) {
        Notifications.Bus.notify(
                new Notification(FlutterSdk.GROUP_DISPLAY_ID, "Open Simulator", FlutterBundle.message("flutter.command.exception", e.getMessage()),
                        NotificationType.ERROR));
      }
    }
  }
}
