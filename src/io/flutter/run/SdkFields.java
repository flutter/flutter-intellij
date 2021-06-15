/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import com.jetbrains.lang.dart.sdk.DartSdk;
import io.flutter.FlutterBundle;
import io.flutter.FlutterInitializer;
import io.flutter.dart.DartPlugin;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRootCache;
import io.flutter.run.common.RunMode;
import io.flutter.run.daemon.DevToolsInstance;
import io.flutter.run.daemon.DevToolsService;
import io.flutter.sdk.FlutterCommand;
import io.flutter.sdk.FlutterSdk;
import io.flutter.settings.FlutterSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Fields used when launching an app using the Flutter SDK (non-bazel).
 */
public class SdkFields {
  private static final Logger LOG = Logger.getInstance(SdkFields.class);
  private @Nullable String filePath;
  private @Nullable String buildFlavor;
  private @Nullable String additionalArgs;
  private @Nullable String attachArgs;

  public SdkFields() {
  }

  /**
   * Creates SDK fields from a Dart file containing a main method.
   */
  public SdkFields(VirtualFile launchFile) {
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

  public boolean hasAdditionalArgs() {
    return additionalArgs != null;
  }

  public String[] getAdditionalArgsParsed() {
    if (hasAdditionalArgs()) {
      assert additionalArgs != null;
      return ParametersListUtil.parse(additionalArgs, false, true, true).toArray(new String[0]);
    }

    return new String[0];
  }

  public String getAttachArgs() {
    return attachArgs;
  }

  public void setAttachArgs(@Nullable String attachArgs) {
    this.attachArgs = attachArgs;
  }

  public boolean hasAttachArgs() {
    return attachArgs != null;
  }

  public String[] getAttachArgsParsed() {
    if (hasAttachArgs()) {
      assert attachArgs != null;
      return ParametersListUtil.parse(attachArgs, false, true, true).toArray(new String[0]);
    }
    return new String[0];
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

    if (PubRootCache.getInstance(project).getRoot(main.get().getAppDir()) == null) {
      throw new RuntimeConfigurationError("Entrypoint isn't within a Flutter pub root");
    }
  }

  /**
   * Create a command to run 'flutter run --machine'.
   */
  public GeneralCommandLine createFlutterSdkRunCommand(
    @NotNull Project project,
    @NotNull RunMode runMode,
    @NotNull FlutterLaunchMode flutterLaunchMode,
    @NotNull FlutterDevice device,
    boolean firstRun) throws ExecutionException {
    final MainFile main = MainFile.verify(filePath, project).get();

    final FlutterSdk flutterSdk = FlutterSdk.getFlutterSdk(project);
    if (flutterSdk == null) {
      throw new ExecutionException(FlutterBundle.message("flutter.sdk.is.not.configured"));
    }

    final PubRoot root = PubRoot.forDirectory(main.getAppDir());
    if (root == null) {
      throw new ExecutionException("Entrypoint isn't within a Flutter pub root");
    }

    final FlutterCommand command;
    String[] args = getAdditionalArgsParsed();
    if (buildFlavor != null) {
      args = ArrayUtil.append(args, "--flavor=" + buildFlavor);
    }
    if (FlutterSettings.getInstance().isShowStructuredErrors() && flutterSdk.getVersion().isDartDefineSupported()) {
      args = ArrayUtil.append(args, "--dart-define=flutter.inspector.structuredErrors=true");
    }

    if (flutterSdk.getVersion().flutterRunSupportsDevToolsUrl()) {
      try {
        final ProgressManager progress = ProgressManager.getInstance();

        final CompletableFuture<DevToolsInstance> devToolsFuture = new CompletableFuture<>();
        progress.runProcessWithProgressSynchronously(() -> {
          progress.getProgressIndicator().setIndeterminate(true);
          try {
            final CompletableFuture<DevToolsInstance> futureInstance = DevToolsService.getInstance(project).getDevToolsInstance();
            if (firstRun) {
              devToolsFuture.complete(futureInstance.get(30, TimeUnit.SECONDS));
            } else {
              // Skip waiting if this isn't the first time running this project. If DevTools isn't available by now, there's likely to be
              // something wrong that won't be fixed by restarting, so we don't want to keep delaying run.
              final DevToolsInstance instance = futureInstance.getNow(null);
              if (instance == null) {
                devToolsFuture.completeExceptionally(new Exception("DevTools instance not available after first run."));
              } else {
                devToolsFuture.complete(instance);
              }
            }
          }
          catch (Exception e) {
            devToolsFuture.completeExceptionally(e);
          }
        }, "Starting DevTools", false, project);
        final DevToolsInstance instance = devToolsFuture.get();
        args = ArrayUtil.append(args, "--devtools-server-address=http://" + instance.host + ":" + instance.port);
        if (firstRun) {
          FlutterInitializer.getAnalytics().sendEvent("devtools", "first-run-success");
        }
      }
      catch (Exception e) {
        LOG.info(e);
        FlutterInitializer.getAnalytics().sendExpectedException("devtools", e);
      }
    }
    command = flutterSdk.flutterRun(root, main.getFile(), device, runMode, flutterLaunchMode, project, args);
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

    String[] args = getAttachArgsParsed();
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
