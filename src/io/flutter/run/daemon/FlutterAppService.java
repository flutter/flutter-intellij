/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineTokenizer;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import io.flutter.FlutterBundle;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkManager;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Launches Flutter Apps.
 */
public class FlutterAppService {
  private final Project project;

  @NotNull
  public static FlutterAppService getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, FlutterAppService.class);
  }

  private FlutterAppService(Project project) {
    this.project = project;
  }

  /**
   * Start a Flutter app using the current Flutter SDK.
   *
   * @param workDir        The directory where the command should be run.
   * @param additionalArgs Additional program args.
   * @param device         The device to use.
   * @param mode           The RunMode to use (release, debug, profile).
   * @param path           Path to the Dart file containing the main method.
   */
  public FlutterApp startFlutterSdkApp(@NotNull String workDir,
                                       @Nullable String additionalArgs,
                                       @Nullable FlutterDevice device,
                                       @NotNull RunMode mode,
                                       @Nullable String path)
    throws ExecutionException {

    final GeneralCommandLine command = createFlutterSdkRunCommand(workDir, additionalArgs, device, mode, path);

    final FlutterApp app = FlutterApp.start(project, mode, command, StringUtil.capitalize(mode.mode()) + "App", "StopApp");

    // Stop the app if the Flutter SDK changes.
    final FlutterSdkManager.Listener sdkListener = new FlutterSdkManager.Listener() {
      @Override
      public void flutterSdkRemoved() {
        app.shutdownAsync();
      }
    };
    FlutterSdkManager.getInstance(project).addListener(sdkListener);
    Disposer.register(project, () -> FlutterSdkManager.getInstance(project).removeListener(sdkListener));

    return app;
  }

  /**
   * Create a command to run 'flutter run --machine'.
   */
  private GeneralCommandLine createFlutterSdkRunCommand(@NotNull String workDir,
                                                        @Nullable String additionalArgs,
                                                        @Nullable FlutterDevice device,
                                                        @NotNull RunMode mode,
                                                        @Nullable String path) throws ExecutionException {

    final FlutterSdk flutterSdk = FlutterSdk.getFlutterSdk(project);
    if (flutterSdk == null) {
      throw new ExecutionException(FlutterBundle.message("flutter.sdk.is.not.configured"));
    }
    final String flutterSdkPath = flutterSdk.getHomePath();
    final String flutterExec = FlutterSdkUtil.pathToFlutterTool(flutterSdkPath);

    final GeneralCommandLine commandLine = new GeneralCommandLine().withWorkDirectory(workDir);
    commandLine.setCharset(CharsetToolkit.UTF8_CHARSET);
    commandLine.setExePath(FileUtil.toSystemDependentName(flutterExec));
    commandLine.withEnvironment(FlutterSdkUtil.FLUTTER_HOST_ENV, FlutterSdkUtil.getFlutterHostEnvValue());
    commandLine.addParameters("run", "--machine");
    if (device != null) {
      commandLine.addParameter("--device-id=" + device.deviceId());
    }
    if (mode == RunMode.PROFILE) {
      commandLine.addParameter("--profile");
    }
    if (mode == RunMode.DEBUG) {
      commandLine.addParameter("--start-paused");
    }
    if (!mode.isReloadEnabled()) {
      commandLine.addParameter("--no-hot");
    }
    if (additionalArgs != null) {
      for (String param : additionalArgs.split(" ")) {
        commandLine.addParameter(param);
      }
    }
    if (path != null) {
      // Make the path relative if possible (to make the command line prettier).
      if (path.startsWith(workDir)) {
        path = path.substring(workDir.length());
        if (path.startsWith("/")) {
          path = path.substring(1);
        }
      }
      path = FileUtil.toSystemDependentName(path);
      commandLine.addParameter(path);
    }

    return commandLine;
  }
}
