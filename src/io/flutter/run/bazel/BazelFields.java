/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazel;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineTokenizer;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import com.jetbrains.lang.dart.sdk.DartSdk;
import io.flutter.FlutterBundle;
import io.flutter.bazel.Workspace;
import io.flutter.dart.DartPlugin;
import io.flutter.run.MainFile;
import io.flutter.run.daemon.FlutterDevice;
import io.flutter.run.daemon.RunMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static io.flutter.run.daemon.RunMode.*;

/**
 * The fields in a Bazel run configuration.
 */
public class BazelFields {

  @Nullable
  private final String bazelTarget;

  private final boolean enableReleaseMode;

  @Nullable
  private final String bazelArgs;
  @Nullable
  private final String additionalArgs;

  BazelFields(@Nullable String bazelTarget, @Nullable String bazelArgs, @Nullable String additionalArgs, boolean enableReleaseMode) {
    this.bazelTarget = bazelTarget;
    this.bazelArgs = bazelArgs;
    this.additionalArgs = additionalArgs;
    this.enableReleaseMode = enableReleaseMode;
  }

  /**
   * Copy constructor
   */
  private BazelFields(@NotNull BazelFields original) {
    bazelTarget = original.bazelTarget;
    enableReleaseMode = original.enableReleaseMode;
    bazelArgs = original.bazelArgs;
    additionalArgs = original.additionalArgs;
  }

  /**
   * Present only for deserializing old run configs.
   */
  @SuppressWarnings("SameReturnValue")
  @Deprecated
  public String getWorkingDirectory() {
    return null;
  }

  @Nullable
  public String getBazelArgs() {
    return bazelArgs;
  }

  @Nullable
  public String getAdditionalArgs() {
    return additionalArgs;
  }


  @Nullable
  public String getBazelTarget() {
    return bazelTarget;
  }

  public boolean getEnableReleaseMode() {
    return enableReleaseMode;
  }

  BazelFields copy() {
    return new BazelFields(this);
  }

  @Nullable
  private String getLaunchScriptFromWorkspace(@NotNull final Project project) {
    final Workspace workspace = getWorkspace(project);
    String launchScript = workspace == null ? null : workspace.getLaunchScript();
    if (launchScript != null) {
      launchScript = workspace.getRoot().getPath() + "/" + launchScript;
    }
    return launchScript;
  }

  @Nullable
  protected Workspace getWorkspace(@NotNull Project project) {
    return Workspace.load(project);
  }

  /**
   * Reports an error in the run config that the user should correct.
   * <p>
   * This will be called while the user is typing into a non-template run config.
   * (See RunConfiguration.checkConfiguration.)
   *
   * @throws RuntimeConfigurationError for an error that that the user must correct before running.
   */
  void checkRunnable(@NotNull final Project project) throws RuntimeConfigurationError {
    final String launchScript = getLaunchScriptFromWorkspace(project);
    checkRunnable(project, launchScript, getBazelTarget());
  }

  /**
   * Reports an error in the run config that the user should correct.
   * <p>
   * This will be called while the user is typing into a non-template run config.
   * (See RunConfiguration.checkConfiguration.)
   *
   * @throws RuntimeConfigurationError for an error that that the user must correct before running.
   */
  public static void checkRunnable(@NotNull final Project project, @Nullable String launchScript,
                                   @Nullable final String bazelTarget) throws RuntimeConfigurationError {
    // The UI only shows one error message at a time.
    // The order we do the checks here determines priority.

    final DartSdk sdk = DartPlugin.getDartSdk(project);
    if (sdk == null) {
      throw new RuntimeConfigurationError(FlutterBundle.message("dart.sdk.is.not.configured"),
                                          () -> DartConfigurable.openDartSettings(project));
    }

    if (launchScript == null) {
      throw new RuntimeConfigurationError(FlutterBundle.message("flutter.run.bazel.noLaunchingScript"));
    }
    final VirtualFile scriptFile = LocalFileSystem.getInstance().findFileByPath(launchScript);
    if (scriptFile == null) {
      throw new RuntimeConfigurationError(
        FlutterBundle.message("flutter.run.bazel.launchingScriptNotFound", FileUtil.toSystemDependentName(launchScript)));
    }

    // check that bazel target is not empty
    if (StringUtil.isEmptyOrSpaces(bazelTarget)) {
      throw new RuntimeConfigurationError(FlutterBundle.message("flutter.run.bazel.noTargetSet"));
    }
    // check that the bazel target starts with "//"
    else if (!bazelTarget.startsWith("//")) {
      throw new RuntimeConfigurationError(FlutterBundle.message("flutter.run.bazel.startWithSlashSlash"));
    }
  }

  /**
   * Returns the command to use to launch the Flutter app. (Via running the Bazel target.)
   */
  GeneralCommandLine getLaunchCommand(@NotNull Project project,
                                      @Nullable FlutterDevice device,
                                      @NotNull RunMode mode)
    throws ExecutionException {
    try {
      checkRunnable(project);
    }
    catch (RuntimeConfigurationError e) {
      throw new ExecutionException(e);
    }

    final Workspace workspace = getWorkspace(project);

    final String launchingScript = getLaunchScriptFromWorkspace(project);
    assert launchingScript != null; // already checked

    final String target = getBazelTarget();
    assert target != null; // already checked

    final String additionalArgs = getAdditionalArgs();

    final GeneralCommandLine commandLine = new GeneralCommandLine().withWorkDirectory(workspace.getRoot().getPath());
    commandLine.setCharset(CharsetToolkit.UTF8_CHARSET);
    commandLine.setExePath(FileUtil.toSystemDependentName(launchingScript));

    // Set the mode. This section needs to match the bazel versions of the flutter_build_mode parameters.
    if (enableReleaseMode) {
      commandLine.addParameters("--define", "flutter_build_mode=release");
    }
    else {
      switch (mode) {
        case PROFILE:
          commandLine.addParameters("--define", "flutter_build_mode=profile");
          break;
        case RUN:
        case DEBUG:
        default:
          // The default mode of a flutter app is debug mode. This is the mode that supports hot reloading.
          // So far as flutter is concerned, there is no difference between debug mode and run mode;
          // the only difference is that a debug mode app will --start-paused.
          commandLine.addParameters("--define", "flutter_build_mode=debug");
          break;
      }
    }

    // User specified additional bazel arguments.
    final CommandLineTokenizer bazelArgsTokenizer = new CommandLineTokenizer(StringUtil.notNullize(bazelArgs));
    while (bazelArgsTokenizer.hasMoreTokens()) {
      commandLine.addParameter(bazelArgsTokenizer.nextToken());
    }
    // (the implicit else here is the debug case)

    // Send in platform architecture based in the device info.
    if (device != null) {

      if (device.isIOS()) {
        // --ios_cpu=[arm64, x86_64]
        final String arch = device.emulator() ? "x86_64" : "arm64";
        commandLine.addParameter("--ios_multi_cpus=" + arch);
      }
      else {
        // --android_cpu=[armeabi-v7a, x86, x86_64]
        String arch = null;
        final String platform = device.platform();
        if (platform != null) {
          switch (platform) {
            case "android-arm":
              arch = "armeabi-v7a";
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
          commandLine.addParameter("--fat_apk_cpu=" + arch);
        }
      }
    }

    commandLine.addParameter(target);

    // Pass additional args to bazel (we currently don't pass --device-id with bazel targets).
    commandLine.addParameter("--");

    // Tell the flutter command-line tools that we want a machine interface on stdio.
    commandLine.addParameter("--machine");

    // Pause the app at startup in order to set breakpoints.
    if (!enableReleaseMode && mode == DEBUG) {
      commandLine.addParameter("--start-paused");
    }

    // User specified additional target arguments.
    final CommandLineTokenizer additionalArgsTokenizer = new CommandLineTokenizer(StringUtil.notNullize(additionalArgs));
    while (additionalArgsTokenizer.hasMoreTokens()) {
      commandLine.addParameter(additionalArgsTokenizer.nextToken());
    }

    // Send in the deviceId.
    if (device != null) {
      commandLine.addParameter("-d");
      commandLine.addParameter(device.deviceId());
    }

    return commandLine;
  }
}
