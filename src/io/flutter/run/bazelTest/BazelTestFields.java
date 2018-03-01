/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import com.intellij.execution.ExecutionException;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The fields in a Bazel test configuration.
 */
public class BazelTestFields {

  private @Nullable String entryFile;
  private @Nullable String launchScript;
  // TODO(jwren) figure out if we want additionalArgs as part this configuration
  //private @Nullable String additionalArgs;
  private @Nullable String bazelTarget;

  BazelTestFields() {
  }

  /**
   * Copy constructor
   */
  private BazelTestFields(@NotNull BazelTestFields original) {
    entryFile = original.entryFile;
    launchScript = original.launchScript;
    // TODO(jwren) figure out if we want additionalArgs as part this configuration
    //additionalArgs = original.additionalArgs;
    bazelTarget = original.bazelTarget;
  }

  /**
   * Create non-template from template.
   */
  private BazelTestFields(@NotNull BazelTestFields template, Workspace w) {
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

  // TODO(jwren) figure out if we want additionalArgs as part this configuration
  //@Nullable
  //public String getAdditionalArgs() {
  //  return additionalArgs;
  //}

  // TODO(jwren) figure out if we want additionalArgs as part this configuration
  //public void setAdditionalArgs(@Nullable String additionalArgs) {
  //  this.additionalArgs = additionalArgs;
  //}

  @Nullable
  public String getBazelTarget() {
    return bazelTarget;
  }

  public void setBazelTarget(@Nullable String bazelTarget) {
    this.bazelTarget = bazelTarget;
  }

  BazelTestFields copy() {
    return new BazelTestFields(this);
  }

  @NotNull
  BazelTestFields copyTemplateToNonTemplate(@NotNull final Project project) {
    final Workspace workspace = WorkspaceCache.getInstance(project).getNow();
    if (workspace == null) return new BazelTestFields(this);
    return new BazelTestFields(this, workspace);
  }

  /**
   * Reports an error in the run config that the user should correct.
   * <p>
   * This will be called while the user is typing into a non-template run config.
   * (See RunConfiguration.checkConfiguration.)
   *
   * @throws RuntimeConfigurationError for an error that that the user must correct before running.
   */
  void checkRunnable(final @NotNull Project project) throws RuntimeConfigurationError {
    // The UI only shows one error message at a time.
    // The order we do the checks here determines priority.

    final DartSdk sdk = DartPlugin.getDartSdk(project);
    if (sdk == null) {
      throw new RuntimeConfigurationError(FlutterBundle.message("dart.sdk.is.not.configured"),
                                          () -> DartConfigurable.openDartSettings(project));
    }

    //final MainFile.Result main = MainFile.verify(entryFile, project);
    //if (!main.canLaunch()) {
    //  throw new RuntimeConfigurationError(main.getError());
    //}

    // Check launcher script
    if (StringUtil.isEmptyOrSpaces(launchScript)) {
      throw new RuntimeConfigurationError(FlutterBundle.message("flutter.run.bazel.noLaunchingScript"));
    }

    final VirtualFile scriptFile = LocalFileSystem.getInstance().findFileByPath(launchScript);
    if (scriptFile == null) {
      throw new RuntimeConfigurationError(
        FlutterBundle.message("flutter.run.bazel.launchingScriptNotFound", FileUtil.toSystemDependentName(launchScript)));
    }

    // Check that bazel target is not empty
    if (StringUtil.isEmptyOrSpaces(bazelTarget)) {
      throw new RuntimeConfigurationError(FlutterBundle.message("flutter.run.bazel.noTargetSet"));
    }
    // Check that the bazel target starts with "//"
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

    // TODO(jwren) figure out if we want additionalArgs as part this configuration
    //final String additionalArgs = getAdditionalArgs();

    final GeneralCommandLine commandLine = new GeneralCommandLine().withWorkDirectory(appDir.getPath());
    commandLine.setCharset(CharsetToolkit.UTF8_CHARSET);
    commandLine.setExePath(FileUtil.toSystemDependentName(launchingScript));

    // Set the mode.
    if (mode != RunMode.DEBUG) {
      commandLine.addParameters("--define", "flutter_build_mode=" + mode.name());
    }

    // TODO(jwren) currently a work in progress
    //// User specified additional arguments.
    //final CommandLineTokenizer argumentsTokenizer = new CommandLineTokenizer(StringUtil.notNullize(additionalArgs));
    //while (argumentsTokenizer.hasMoreTokens()) {
    //  final String token = argumentsTokenizer.nextToken();
    //  if (token.equals("--")) {
    //    break;
    //  }
    //  commandLine.addParameter(token);
    //}

    commandLine.addParameter(target);

    // TODO(jwren) currently a work in progress
    //// Pass additional args to bazel (we currently don't pass --device-id with bazel targets).
    //commandLine.addParameter("--");
    //
    //// Tell the flutter command-line tools that we want a machine interface on stdio.
    //commandLine.addParameter("--machine");
    //
    //if (FlutterSettings.getInstance().getPreviewDart2()) {
    //  commandLine.addParameter("--preview-dart-2");
    //}
    //
    //// Pause the app at startup in order to set breakpoints.
    //if (mode == RunMode.DEBUG) {
    //  commandLine.addParameter("--start-paused");
    //}
    //
    //// More user-specified args.
    //while (argumentsTokenizer.hasMoreTokens()) {
    //  commandLine.addParameter(argumentsTokenizer.nextToken());
    //}
    //
    //// Send in the deviceId.
    //if (device != null) {
    //  commandLine.addParameter("-d");
    //  commandLine.addParameter(device.deviceId());
    //}

    return commandLine;
  }
}
