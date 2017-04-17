/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import com.jetbrains.lang.dart.sdk.DartSdk;
import io.flutter.FlutterBundle;
import io.flutter.dart.DartPlugin;
import io.flutter.run.daemon.FlutterDevice;
import io.flutter.run.daemon.RunMode;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fields used when launching an app using the Flutter SDK (non-bazel).
 */
public class SdkFields {
  private @Nullable String filePath;
  private @Nullable String additionalArgs;

  public SdkFields() {
  }

  /**
   * Creates SDK fields from a Dart file containing a main method.
   */
  public SdkFields(VirtualFile launchFile, Project project) {
    filePath = launchFile.getPath();
  }

  @Nullable
  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(final @Nullable String path) {
    filePath = path;
  }

  @Nullable
  public String getAdditionalArgs() {
    return additionalArgs;
  }

  public void setAdditionalArgs(final @Nullable String additionalArgs) {
    this.additionalArgs = additionalArgs;
  }

  /**
   * Present only for deserializing old run configs.
   */
  @SuppressWarnings("SameReturnValue")
  @Deprecated
  @Nullable
  public String getWorkingDirectory() {
    return null;
  }

  /**
   * Present only for deserializing old run configs.
   */
  @SuppressWarnings("EmptyMethod")
  @Deprecated
  public void setWorkingDirectory(final @Nullable String dir) {
  }

  /**
   * Reports any errors that the user should correct.
   * <p>This will be called while the user is typing; see RunConfiguration.checkConfiguration.
   *
   * @throws RuntimeConfigurationError for an error that that the user must correct before running.
   */
  void checkRunnable(final @NotNull Project project) throws RuntimeConfigurationError {
    // TODO(pq): consider validating additional args values
    checkSdk(project);
    final MainFile.Result main = MainFile.verify(filePath, project);
    if (!MainFile.verify(filePath, project).canLaunch()) {
      throw new RuntimeConfigurationError(main.getError());
    }
  }

  /**
   * Create a command to run 'flutter run --machine'.
   */
  public GeneralCommandLine createFlutterSdkRunCommand(Project project, @Nullable FlutterDevice device,
                                                       @NotNull RunMode mode) throws ExecutionException {

    final MainFile main = MainFile.verify(filePath, project).get();
    final String appPath = main.getAppDir().getPath();

    final FlutterSdk flutterSdk = FlutterSdk.getFlutterSdk(project);
    if (flutterSdk == null) {
      throw new ExecutionException(FlutterBundle.message("flutter.sdk.is.not.configured"));
    }
    final String flutterSdkPath = flutterSdk.getHomePath();
    final String flutterExec = FlutterSdkUtil.pathToFlutterTool(flutterSdkPath);

    final GeneralCommandLine commandLine = new GeneralCommandLine().withWorkDirectory(appPath);
    commandLine.setCharset(CharsetToolkit.UTF8_CHARSET);
    commandLine.setExePath(FileUtil.toSystemDependentName(flutterExec));
    commandLine.withEnvironment(FlutterSdkUtil.FLUTTER_HOST_ENV, FlutterSdkUtil.getFlutterHostEnvValue());
    commandLine.addParameters("run", "--machine");
    if (device != null) {
      commandLine.addParameter("--device-id=" + device.deviceId());
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
    // Make the path relative if possible (to make the command line prettier).
    assert main.getFile().getPath().startsWith(appPath);
    assert !appPath.endsWith("/");
    final String mainPath = main.getFile().getPath().substring(appPath.length() + 1);
    commandLine.addParameter(FileUtil.toSystemDependentName(mainPath));

    return commandLine;
  }

  SdkFields copy() {
    final SdkFields copy = new SdkFields();
    copy.setFilePath(filePath);
    return copy;
  }

  private static void checkSdk(@NotNull Project project) throws RuntimeConfigurationError {
    // TODO(skybrian) shouldn't this be flutter SDK?

    final DartSdk sdk = DartPlugin.getDartSdk(project);
    if (sdk == null) {
      throw new RuntimeConfigurationError(FlutterBundle.message("dart.sdk.is.not.configured"),
                                          () -> DartConfigurable.openDartSettings(project));
    }
  }
}
