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
import io.flutter.bazel.WorkspaceCache;
import io.flutter.dart.DartPlugin;
import io.flutter.run.MainFile;
import io.flutter.run.daemon.FlutterDevice;
import io.flutter.run.daemon.RunMode;
import io.flutter.settings.FlutterSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The fields in a Bazel run configuration.
 */
public class BazelFields {

  private @Nullable String entryFile;
  private @Nullable String launchScript;
  private @Nullable String bazelTarget;
  private boolean enableReleaseMode = false;
  private @Nullable String additionalArgs;

  BazelFields() {
  }

  /**
   * Copy constructor
   */
  private BazelFields(@NotNull BazelFields original) {
    entryFile = original.entryFile;
    launchScript = original.launchScript;
    bazelTarget = original.bazelTarget;
    enableReleaseMode = original.enableReleaseMode;
    additionalArgs = original.additionalArgs;
  }

  /**
   * Create non-template from template.
   */
  private BazelFields(@NotNull BazelFields template, Workspace w) {
    this(template);
    if (StringUtil.isEmptyOrSpaces(launchScript)) {
      launchScript = w.getLaunchScript();
      if (launchScript != null && !launchScript.startsWith("/")) {
        launchScript = w.getRoot().getPath() + "/" + launchScript;
      }
    }
  }

  /**
   * The file containing the main function that starts the Flutter app.
   */
  @Nullable
  public String getEntryFile() {
    return entryFile;
  }

  public void setEntryFile(final @Nullable String entryFile) {
    this.entryFile = entryFile;
  }

  /**
   * Present only for deserializing old run configs.
   */
  @SuppressWarnings("SameReturnValue")
  @Deprecated
  public String getWorkingDirectory() {
    return null;
  }

  /**
   * Used only for deserializing old run configs.
   */
  @Deprecated
  public void setWorkingDirectory(final @Nullable String workDir) {
    if (entryFile == null && workDir != null) {
      entryFile = workDir + "/lib/main.dart";
    }
  }

  @Nullable
  public String getLaunchingScript() {
    return launchScript;
  }

  public void setLaunchingScript(@Nullable String launchScript) {
    this.launchScript = launchScript;
  }

  @Nullable
  public String getAdditionalArgs() {
    return additionalArgs;
  }

  public void setAdditionalArgs(@Nullable String additionalArgs) {
    this.additionalArgs = additionalArgs;
  }

  @Nullable
  public String getBazelTarget() {
    return bazelTarget;
  }

  public void setBazelTarget(@Nullable String bazelTarget) {
    this.bazelTarget = bazelTarget;
  }

  public boolean getEnableReleaseMode() {
    return enableReleaseMode;
  }

  public void setEnableReleaseMode(boolean enableReleaseMode) {
    this.enableReleaseMode = enableReleaseMode;
  }

  BazelFields copy() {
    return new BazelFields(this);
  }

  @NotNull
  BazelFields copyTemplateToNonTemplate(@NotNull final Project project) {
    final Workspace workspace = WorkspaceCache.getInstance(project).getNow();
    if (workspace == null) return new BazelFields(this);
    return new BazelFields(this, workspace);
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
    checkRunnable(project, getEntryFile(), getLaunchingScript(), getBazelTarget());
  }

  /**
   * Reports an error in the run config that the user should correct.
   * <p>
   * This will be called while the user is typing into a non-template run config.
   * (See RunConfiguration.checkConfiguration.)
   *
   * @throws RuntimeConfigurationError for an error that that the user must correct before running.
   */
  public static void checkRunnable(@NotNull final Project project, @Nullable final String entryFile, @Nullable final String launchScript,
                                   @Nullable final String bazelTarget) throws RuntimeConfigurationError {
    // The UI only shows one error message at a time.
    // The order we do the checks here determines priority.

    final DartSdk sdk = DartPlugin.getDartSdk(project);
    if (sdk == null) {
      throw new RuntimeConfigurationError(FlutterBundle.message("dart.sdk.is.not.configured"),
                                          () -> DartConfigurable.openDartSettings(project));
    }

    final MainFile.Result main = MainFile.verify(entryFile, project);
    if (!main.canLaunch()) {
      throw new RuntimeConfigurationError(main.getError());
    }

    // check launcher script
    if (StringUtil.isEmptyOrSpaces(launchScript)) {
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

    final VirtualFile appDir = MainFile.verify(entryFile, project).get().getAppDir();

    final String launchingScript = getLaunchingScript();
    assert launchingScript != null; // already checked

    final String target = getBazelTarget();
    assert target != null; // already checked

    final String additionalArgs = getAdditionalArgs();

    final GeneralCommandLine commandLine = new GeneralCommandLine().withWorkDirectory(appDir.getPath());
    commandLine.setCharset(CharsetToolkit.UTF8_CHARSET);
    commandLine.setExePath(FileUtil.toSystemDependentName(launchingScript));

    // Set the mode.
    if (enableReleaseMode) {
      commandLine.addParameters("--define", "flutter_build_mode=release");
    }
    else if (mode != RunMode.DEBUG) {
      commandLine.addParameters("--define", "flutter_build_mode=" + mode.name());
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

    if (FlutterSettings.getInstance().isEnablePreviewDart2()) {
      commandLine.addParameter("--preview-dart-2");
    }
    else if (FlutterSettings.getInstance().isDisablePreviewDart2()) {
      commandLine.addParameter("--no-preview-dart-2");
    }

    // Pause the app at startup in order to set breakpoints.
    if (!enableReleaseMode && mode == RunMode.DEBUG) {
      commandLine.addParameter("--start-paused");
    }

    // User specified additional target arguments.
    final CommandLineTokenizer argumentsTokenizer = new CommandLineTokenizer(StringUtil.notNullize(additionalArgs));
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
