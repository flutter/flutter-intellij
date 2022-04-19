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
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import com.jetbrains.lang.dart.sdk.DartSdk;
import io.flutter.FlutterBundle;
import io.flutter.FlutterMessages;
import io.flutter.bazel.Workspace;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.dart.DartPlugin;
import io.flutter.run.FlutterDevice;
import io.flutter.run.common.RunMode;
import io.flutter.run.daemon.DevToolsInstance;
import io.flutter.run.daemon.DevToolsService;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.ElementIO;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static io.flutter.run.common.RunMode.DEBUG;
import static io.flutter.run.common.RunMode.PROFILE;

/**
 * The fields in a Bazel run configuration.
 * <p>
 * This class is immutable.
 */
public class BazelFields {
  private static final Logger LOG = Logger.getInstance(BazelFields.class);

  /**
   * The Bazel target or Dart file to invoke.
   */
  @Nullable
  private final String target;

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
   * This is to set a DevToolsService ahead of time, intended for testing.
   */
  @Nullable
  private final DevToolsService devToolsService;

  /**
   * Parameters to pass to Flutter, such as --start-paused.
   */
  @Nullable
  private final String additionalArgs;

  BazelFields(@Nullable String target, @Nullable String bazelArgs, @Nullable String additionalArgs, boolean enableReleaseMode) {
    this(target, bazelArgs, additionalArgs, enableReleaseMode, null);
  }

  BazelFields(@Nullable String target,
              @Nullable String bazelArgs,
              @Nullable String additionalArgs,
              boolean enableReleaseMode,
              @Nullable DevToolsService devToolsService) {
    this.target = target;
    this.bazelArgs = bazelArgs;
    this.additionalArgs = additionalArgs;
    this.enableReleaseMode = enableReleaseMode;
    this.devToolsService = devToolsService;
  }

  /**
   * Copy constructor
   */
  BazelFields(@NotNull BazelFields original) {
    target = original.target;
    enableReleaseMode = original.enableReleaseMode;
    bazelArgs = original.bazelArgs;
    additionalArgs = original.additionalArgs;
    devToolsService = original.devToolsService;
  }

  @Nullable
  public String getBazelArgs() {
    return bazelArgs;
  }

  @Nullable
  public String getAdditionalArgs() {
    return additionalArgs;
  }

  /**
   * This can be either a bazel or a dart target.
   */
  @Nullable
  public String getTarget() {
    return target;
  }

  public boolean getEnableReleaseMode() {
    return enableReleaseMode;
  }

  BazelFields copy() {
    return new BazelFields(this);
  }

  @Nullable
  private String getRunScriptFromWorkspace(@NotNull final Project project) {
    final Workspace workspace = getWorkspace(project);
    String runScript = workspace == null ? null : workspace.getRunScript();
    if (runScript != null) {
      runScript = workspace.getRoot().getPath() + "/" + runScript;
    }
    return runScript;
  }

  // TODO(djshuckerow): this is dependency injection; switch this to a framework as we need more DI.
  @Nullable
  protected Workspace getWorkspace(@NotNull Project project) {
    return WorkspaceCache.getInstance(project).get();
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

    final String runScript = getRunScriptFromWorkspace(project);
    if (runScript == null) {
      throw new RuntimeConfigurationError(FlutterBundle.message("flutter.run.bazel.noLaunchingScript"));
    }

    final VirtualFile scriptFile = LocalFileSystem.getInstance().findFileByPath(runScript);
    if (scriptFile == null) {
      throw new RuntimeConfigurationError(
        FlutterBundle.message("flutter.run.bazel.launchingScriptNotFound", FileUtil.toSystemDependentName(runScript)));
    }

    // Check that target field is populated.
    if (StringUtil.isEmptyOrSpaces(target)) {
      throw new RuntimeConfigurationError(FlutterBundle.message("flutter.run.bazel.noBazelOrDartTargetSet"));
    }
    else if (!target.endsWith("dart") && !target.startsWith("//")) {
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

    final String launchingScript = getRunScriptFromWorkspace(project);
    assert launchingScript != null; // already checked
    assert workspace != null; // if the workspace is null, then so is the launching script, therefore this was already checked.

    final String target = getTarget();
    assert target != null; // already checked

    final String additionalArgs = getAdditionalArgs();

    final GeneralCommandLine commandLine = new GeneralCommandLine()
      .withWorkDirectory(workspace.getRoot().getPath());
    commandLine.setCharset(StandardCharsets.UTF_8);
    commandLine.setExePath(FileUtil.toSystemDependentName(launchingScript));

    final String inputBazelArgs = StringUtil.notNullize(bazelArgs);
    if (!inputBazelArgs.isEmpty()) {
      commandLine.addParameter(String.format("--bazel-options=%s", inputBazelArgs));
    }

    // Potentially add the flag related to build mode.
    if (enableReleaseMode) {
      commandLine.addParameter("--release");
    }
    else if (mode.equals(PROFILE)) {
      commandLine.addParameter("--profile");
    }

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

    final String enableBazelHotRestartParam = "--enable-google3-hot-reload";
    final String disableBazelHotRestartParam = "--no-enable-google3-hot-reload";
    final boolean hasEnabledArg = StringUtil.notNullize(additionalArgs).contains(enableBazelHotRestartParam);
    final boolean hasDisabledArg = StringUtil.notNullize(additionalArgs).contains(disableBazelHotRestartParam);
    if (!FlutterSettings.getInstance().isEnableBazelHotRestart() && hasDisabledArg) {
      final Notification notification = new Notification(
        FlutterMessages.FLUTTER_NOTIFICATION_GROUP_ID,
        "Google3-specific hot restart is disabled by default",
        "You can now remove this flag from your configuration's additional args: " + disableBazelHotRestartParam,
        NotificationType.INFORMATION);
      Notifications.Bus.notify(notification, project);
    }
    else if (FlutterSettings.getInstance().isEnableBazelHotRestart() && !hasEnabledArg && !hasDisabledArg) {
      commandLine.addParameter(enableBazelHotRestartParam);
    }

    // Send in the deviceId.
    if (device != null) {
      commandLine.addParameter("-d");
      commandLine.addParameter(device.deviceId());

      final String message = workspace.getUpdatedIosRunMessage();
      if (message != null && FlutterSettings.getInstance().isShowBazelIosRunNotification()) {
        final String title = device.isIOS() ? "Running iOS apps has improved!" : "Try running an iOS app!";
        final Notification notification = new Notification(
          FlutterMessages.FLUTTER_NOTIFICATION_GROUP_ID,
          title,
          message,
          NotificationType.INFORMATION);
        Notifications.Bus.notify(notification, project);
        FlutterSettings.getInstance().setShowBazelIosRunNotification(false);
      }
    }

    try {
      final ProgressManager progress = ProgressManager.getInstance();

      final CompletableFuture<DevToolsInstance> devToolsFuture = new CompletableFuture<>();
      progress.runProcessWithProgressSynchronously(() -> {
        progress.getProgressIndicator().setIndeterminate(true);
        try {
          final DevToolsService service = this.devToolsService == null ? DevToolsService.getInstance(project) : this.devToolsService;
          final DevToolsInstance instance = service.getDevToolsInstance().get(30, TimeUnit.SECONDS);
          if (instance != null) {
            devToolsFuture.complete(instance);
          } else {
            devToolsFuture.completeExceptionally(new Exception("DevTools instance not available."));
          }
        }
        catch (Exception e) {
          devToolsFuture.completeExceptionally(e);
        }
      }, "Starting DevTools", false, project);
      final DevToolsInstance instance = devToolsFuture.get();
      commandLine.addParameter("--devtools-server-address=http://" + instance.host + ":" + instance.port);
    }
    catch (Exception e) {
      LOG.info(e);
    }

    commandLine.addParameter(target);

    return commandLine;
  }

  public void writeTo(Element element) {
    ElementIO.addOption(element, "target", target);
    ElementIO.addOption(element, "bazelArgs", bazelArgs);
    ElementIO.addOption(element, "additionalArgs", additionalArgs);
    ElementIO.addOption(element, "enableReleaseMode", Boolean.toString(enableReleaseMode));
  }

  public static BazelFields readFrom(Element element) {
    final Map<String, String> options = ElementIO.readOptions(element);

    // Use old field name of bazelTarget if the newer one has not been set.
    final String bazelOrDartTarget =
      options.get("target") != null ? options.get("target") : options.get("bazelTarget");
    final String bazelArgs = options.get("bazelArgs");
    final String additionalArgs = options.get("additionalArgs");
    final String enableReleaseMode = options.get("enableReleaseMode");

    try {
      return new BazelFields(bazelOrDartTarget, bazelArgs, additionalArgs, Boolean.parseBoolean(enableReleaseMode));
    }
    catch (IllegalArgumentException e) {
      throw new InvalidDataException(e.getMessage());
    }
  }
}
