/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineTokenizer;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import io.flutter.FlutterBundle;
import io.flutter.FlutterInitializer;
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
   * @param workDir The directory where the command should be run.
   * @param additionalArgs Additional program args.
   * @param device The device to use.
   * @param mode The RunMode to use (release, debug, profile).
   * @param path Path to the Dart file containing the main method.
   */
  public FlutterApp startFlutterSdkApp(@NotNull String workDir,
                                       @Nullable String additionalArgs,
                                       @Nullable FlutterDevice device,
                                       @NotNull RunMode mode,
                                       @Nullable String path)
    throws ExecutionException {

    final GeneralCommandLine command = createFlutterSdkRunCommand(workDir, additionalArgs, device, mode, path);

    final FlutterApp app = startApp(mode, command, StringUtil.capitalize(mode.mode()) + "App", "StopApp");

    // Stop the app if the Flutter SDK changes.
    final FlutterSdkManager.Listener sdkListener = new FlutterSdkManager.Listener() {
      @Override
      public void flutterSdkRemoved() {
        app.shutdownAsync();
      }
    };
    FlutterSdkManager.getInstance().addListener(sdkListener);
    Disposer.register(project, () -> FlutterSdkManager.getInstance().removeListener(sdkListener));

    return app;
  }

  public FlutterApp startBazelApp(@NotNull String projectDir,
                                  @NotNull String launchingScript,
                                  @Nullable FlutterDevice device,
                                  @NotNull RunMode mode,
                                  @NotNull String bazelTarget,
                                  @Nullable String additionalArguments)
    throws ExecutionException {
    final GeneralCommandLine command = createBazelRunCommand(
      projectDir, device, mode, launchingScript, bazelTarget, additionalArguments);

    return startApp(mode, command, StringUtil.capitalize(mode.mode()) + "BazelApp", "StopBazelApp");
  }

  @NotNull
  private FlutterApp startApp(@NotNull RunMode mode,
                              @NotNull GeneralCommandLine command,
                              @NotNull String analyticsStart,
                              @NotNull String analyticsStop)
    throws ExecutionException {

    final ProcessHandler process = new OSProcessHandler(command);
    Disposer.register(project, process::destroyProcess);

    // Send analytics for the start and stop events.
    FlutterInitializer.sendAnalyticsAction(analyticsStart);
    process.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(ProcessEvent event) {
        FlutterInitializer.sendAnalyticsAction(analyticsStop);
      }
    });

    final DaemonApi api = new DaemonApi(process);
    final FlutterApp app = new FlutterApp(mode, process, api);
    api.listen(process, new FlutterAppListener(app, project));

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

  private GeneralCommandLine createBazelRunCommand(@NotNull String workDir,
                                                   @Nullable FlutterDevice device,
                                                   @NotNull RunMode mode,
                                                   @NotNull String launchingScript,
                                                   @NotNull String bazelTarget,
                                                   @Nullable String additionalArguments) {
    final GeneralCommandLine commandLine = new GeneralCommandLine().withWorkDirectory(workDir);
    commandLine.setCharset(CharsetToolkit.UTF8_CHARSET);
    commandLine.setExePath(FileUtil.toSystemDependentName(launchingScript));

    // Set the mode.
    if (mode != RunMode.DEBUG) {
      commandLine.addParameters("--define", "flutter_build_mode=" + mode.name());
    }

    // Send in platform architecture based in the device info.
    if (device != null) {

      if (device.isIOS()) {
        // --ios_cpu=[arm64, x86_64]
        final String arch = device.emulator() ? "x86_64" : "arm64";
        commandLine.addParameter("--ios_cpu=" + arch);
      }
      else {
        // --android_cpu=[armeabi, x86, x86_64]
        String arch = null;
        final String platform = device.platform();
        if (platform != null) {
          switch (platform) {
            case "android-arm":
              arch = "armeabi";
              break;
            case "android-x86":
              arch = "x86";
              break;
            case "android-x64":
              arch = "x86_64";
              break;
            case "linux-x64":
              arch = "x86_64";
              break;
          }
        }

        if (arch != null) {
          commandLine.addParameter("--android_cpu=" + arch);
        }
      }
    }

    // User specified additional arguments.
    final CommandLineTokenizer argumentsTokenizer = new CommandLineTokenizer(StringUtil.notNullize(additionalArguments));
    while (argumentsTokenizer.hasMoreTokens()) {
      final String token = argumentsTokenizer.nextToken();
      if (token.equals("--")) {
        break;
      }
      commandLine.addParameter(token);
    }

    // Append _run[_hot] to bazelTarget.
    if (!bazelTarget.endsWith("_run") && !bazelTarget.endsWith(("_hot"))) {
      bazelTarget += "_run";
      if (mode.isReloadEnabled()) {
        bazelTarget += "_hot";
      }
    }
    commandLine.addParameter(bazelTarget);

    // Pass additional args to bazel (we currently don't pass --device-id with bazel targets).
    commandLine.addParameter("--");

    // Tell the flutter tommand-line tools that we want a machine interface on stdio.
    commandLine.addParameters("--machine");

    // Pause the app at startup in order to set breakpoints.
    if (mode == RunMode.DEBUG) {
      commandLine.addParameter("--start-paused");
    }

    // More user-specified args.
    while (argumentsTokenizer.hasMoreTokens()) {
      commandLine.addParameter(argumentsTokenizer.nextToken());
    }

    // Send in the deviceId.
    if (device != null) {
      commandLine.addParameter("-d");
      commandLine.addParameter(device.deviceId());
    }

    return commandLine;
  }
}
