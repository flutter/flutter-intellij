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
import com.intellij.openapi.util.InvalidDataException;
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
import io.flutter.run.daemon.FlutterDevice;
import io.flutter.run.daemon.RunMode;
import io.flutter.utils.ElementIO;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static io.flutter.run.daemon.RunMode.DEBUG;

/**
 * The fields in a Bazel run configuration.
 * <p>
 * This class is immutable.
 */
public class BazelFields {

  /**
   * The Bazel target to invoke.
   */
  @Nullable
  private final String bazelTarget;

  /**
   * Whether or not to run the app with --define flutter_build_mode=release.
   *
   * <p>
   * If this is not set, then the flutter_build_mode will depend on which button
   * the user pressed to run the app.
   * <ul>
   * <li>If the user pressed 'run' or 'debug', then flutter_build_mode=debug.</li>
   * <li>If the user pressed 'profile', then flutter_build_mode=profile.</li>
   * </ul>
   *
   * <p>
   * If the user overrides --define flutter_build_mode in {@link #bazelArgs}, then this field will be ignored.
   */
  private final boolean enableReleaseMode;

  /**
   * Parameters to pass to Bazel, such as --define release_channel=beta3.
   */
  @Nullable
  private final String bazelArgs;

  /**
   * Parameters to pass to Flutter, such as --start-paused.
   */
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
  BazelFields(@NotNull BazelFields original) {
    bazelTarget = original.bazelTarget;
    enableReleaseMode = original.enableReleaseMode;
    bazelArgs = original.bazelArgs;
    additionalArgs = original.additionalArgs;
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

  // TODO(djshuckerow): this is dependency injection; switch this to a framework as we need more DI.
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

    // The UI only shows one error message at a time.
    // The order we do the checks here determines priority.

    final DartSdk sdk = DartPlugin.getDartSdk(project);
    if (sdk == null) {
      throw new RuntimeConfigurationError(FlutterBundle.message("dart.sdk.is.not.configured"),
                                          () -> DartConfigurable.openDartSettings(project));
    }

    final String launchScript = getLaunchScriptFromWorkspace(project);

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
    assert workspace != null; // if the workspace is null, then so is the launching script, therefore this was already checked.

    final String target = getBazelTarget();
    assert target != null; // already checked

    final String additionalArgs = getAdditionalArgs();

    final GeneralCommandLine commandLine = new GeneralCommandLine()
      .withWorkDirectory(workspace.getRoot().getPath());
    commandLine.setCharset(CharsetToolkit.UTF8_CHARSET);
    commandLine.setExePath(FileUtil.toSystemDependentName(launchingScript));

    // If the user hasn't overridden the flutter_build_mode, then
    if (!StringUtil.notNullize(bazelArgs).matches(".*--define[ =]flutter_build_mode.*")) {

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
    }

    // User specified additional bazel arguments.
    final CommandLineTokenizer bazelArgsTokenizer = new CommandLineTokenizer(
      StringUtil.notNullize(bazelArgs));
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
    final CommandLineTokenizer additionalArgsTokenizer = new CommandLineTokenizer(
      StringUtil.notNullize(additionalArgs));
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


  public void writeTo(Element element) {
    ElementIO.addOption(element, "bazelTarget", bazelTarget);
    ElementIO.addOption(element, "bazelArgs", bazelArgs);
    ElementIO.addOption(element, "additionalArgs", additionalArgs);
    ElementIO.addOption(element, "enableReleaseMode", Boolean.toString(enableReleaseMode));
  }

  public static BazelFields readFrom(Element element) {
    final Map<String, String> options = ElementIO.readOptions(element);

    final String bazelTarget = options.get("bazelTarget");
    final String bazelArgs = options.get("bazelArgs");
    final String additionalArgs = options.get("additionalArgs");
    final String enableReleaseMode = options.get("enableReleaseMode");

    try {
      return new BazelFields(bazelTarget, bazelArgs, additionalArgs, Boolean.valueOf(enableReleaseMode));
    }
    catch (IllegalArgumentException e) {
      throw new InvalidDataException(e.getMessage());
    }
  }
}
