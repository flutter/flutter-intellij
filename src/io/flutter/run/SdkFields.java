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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import com.jetbrains.lang.dart.sdk.DartSdk;
import io.flutter.FlutterBundle;
import io.flutter.dart.DartPlugin;
import io.flutter.pub.PubRoot;
import io.flutter.run.daemon.FlutterDevice;
import io.flutter.run.daemon.RunMode;
import io.flutter.sdk.FlutterCommand;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fields used when launching an app using the Flutter SDK (non-bazel).
 */
public class SdkFields {
  private @Nullable String filePath;
  private @Nullable String buildFlavor;
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
  public String getBuildFlavor() {
    return buildFlavor;
  }

  public void setBuildFlavor(final @Nullable String buildFlavor) {
    this.buildFlavor = buildFlavor;
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
  void checkRunnable(@NotNull Project project) throws RuntimeConfigurationError {
    // TODO(pq): consider validating additional args values
    checkSdk(project);
    final MainFile.Result main = MainFile.verify(filePath, project);
    if (!main.canLaunch()) {
      throw new RuntimeConfigurationError(main.getError());
    }
    if (PubRoot.forDirectory(main.get().getAppDir()) == null) {
      throw new RuntimeConfigurationError("Entrypoint isn't within a Flutter pub root");
    }
  }

  /**
   * Create a command to run 'flutter run --machine'.
   */
  public GeneralCommandLine createFlutterSdkRunCommand(@NotNull Project project,
                                                       @NotNull RunMode runMode,
                                                       @NotNull FlutterLaunchMode flutterLaunchMode,
                                                       @Nullable FlutterDevice device
  ) throws ExecutionException {
    final MainFile main = MainFile.verify(filePath, project).get();

    final FlutterSdk flutterSdk = FlutterSdk.getFlutterSdk(project);
    if (flutterSdk == null) {
      throw new ExecutionException(FlutterBundle.message("flutter.sdk.is.not.configured"));
    }

    final PubRoot root = PubRoot.forDirectory(main.getAppDir());
    if (root == null) {
      throw new ExecutionException("Entrypoint isn't within a Flutter pub root");
    }

    String[] args = additionalArgs == null ? new String[]{} : additionalArgs.split(" ");
    if (buildFlavor != null) {
      args = ArrayUtil.append(args, "--flavor=" + buildFlavor);
    }
    final FlutterCommand command = flutterSdk.flutterRun(root, main.getFile(), device, runMode, flutterLaunchMode, project, args);
    return command.createGeneralCommandLine(project);
  }

  /**
   * Create a command to run 'flutter attach --machine'.
   */
  public GeneralCommandLine createFlutterSdkAttachCommand(@NotNull Project project,
                                                          @NotNull FlutterLaunchMode flutterLaunchMode,
                                                          @Nullable FlutterDevice device) throws ExecutionException {
    final MainFile main = MainFile.verify(filePath, project).get();

    final FlutterSdk flutterSdk = FlutterSdk.getFlutterSdk(project);
    if (flutterSdk == null) {
      throw new ExecutionException(FlutterBundle.message("flutter.sdk.is.not.configured"));
    }

    final PubRoot root = PubRoot.forDirectory(main.getAppDir());
    if (root == null) {
      throw new ExecutionException("Entrypoint isn't within a Flutter pub root");
    }

    String[] args = additionalArgs == null ? new String[]{} : additionalArgs.split(" ");
    if (buildFlavor != null) {
      args = ArrayUtil.append(args, "--flavor=" + buildFlavor);
    }
    final FlutterCommand command = flutterSdk.flutterAttach(root, main.getFile(), device, flutterLaunchMode, args);
    return command.createGeneralCommandLine(project);
  }

  SdkFields copy() {
    final SdkFields copy = new SdkFields();
    copy.setFilePath(filePath);
    copy.setAdditionalArgs(additionalArgs);
    copy.setBuildFlavor(buildFlavor);
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
