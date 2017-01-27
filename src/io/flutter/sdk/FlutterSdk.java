/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.content.MessageView;
import com.intellij.util.ArrayUtil;
import com.jetbrains.lang.dart.sdk.DartSdk;
import io.flutter.FlutterBundle;
import io.flutter.FlutterMessages;
import io.flutter.FlutterInitializer;
import io.flutter.console.FlutterConsoleHelper;
import io.flutter.dart.DartPlugin;
import io.flutter.run.FlutterRunConfiguration;
import io.flutter.run.FlutterRunConfigurationType;
import io.flutter.run.FlutterRunnerParameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class FlutterSdk {
  public static final String FLUTTER_SDK_GLOBAL_LIB_NAME = "Flutter SDK";
  private static final Logger LOG = Logger.getInstance(FlutterSdk.class);
  private static final AtomicBoolean inProgress = new AtomicBoolean(false);
  private final @NotNull String myHomePath;
  private final @NotNull FlutterSdkVersion myVersion;

  private FlutterSdk(@NotNull final String homePath, @Nullable final String version) {
    myHomePath = homePath;
    myVersion = FlutterSdkVersion.forVersionString(version);
  }

  private FlutterSdk(@NotNull final String homePath) {
    this(homePath, FlutterSdkUtil.getSdkVersion(homePath));
  }

  /**
   * At the moment per-project SDK configuration is not supported, so this method returns the same as {@link #getGlobalFlutterSdk()}.
   * Prefer using this method if you have Project in hands.
   */
  @Nullable
  public static FlutterSdk getFlutterSdk(@NotNull final Project project) {
    return getFlutterSdkByDartSdk(DartPlugin.getDartSdk(project));
  }

  /**
   * Callers should instead prefer {@link #getFlutterSdk(Project)}.
   */
  @Nullable
  public static FlutterSdk getGlobalFlutterSdk() {
    return getFlutterSdkByDartSdk(DartSdk.getGlobalDartSdk());
  }

  @Nullable
  private static FlutterSdk getFlutterSdkByDartSdk(@Nullable final DartSdk dartSdk) {
    final String suffix = "/bin/cache/dart-sdk";
    final String dartPath = dartSdk == null ? null : dartSdk.getHomePath();
    return dartPath != null && dartPath.endsWith(suffix) ? forPath(dartPath.substring(0, dartPath.length() - suffix.length()))
                                                         : null;
  }

  public static FlutterSdk forPath(@NotNull final String path) {
    return FlutterSdkUtil.isFlutterSdkHome(path) ? new FlutterSdk(path) : null;
  }

  private static void printExitMessage(@Nullable Project project, @Nullable Module module, int exitCode) {
    if (project == null && module == null) {
      return;
    }

    final Project p = project == null ? module.getProject() : project;
    MessageView.SERVICE.getInstance(p).runWhenInitialized(() -> {
      final ConsoleView console = FlutterConsoleHelper.findConsoleView(p, module);
      if (console != null) {
        console.print(
          FlutterBundle.message("finished.with.exit.code.text.message", exitCode), ConsoleViewContentType.SYSTEM_OUTPUT);
      }
    });
  }

  private static void start(@NotNull OSProcessHandler handler) {
    DartPlugin.setPubActionInProgress(true);
    try {
      handler.startNotify();
    }
    finally {
      DartPlugin.setPubActionInProgress(false);
    }
  }

  public void run(@NotNull Command cmd,
                  @Nullable Module module,
                  @Nullable VirtualFile workingDir,
                  @Nullable ProcessListener listener,
                  @NotNull String... args)
    throws ExecutionException {
    final String flutterPath = FlutterSdkUtil.pathToFlutterTool(getHomePath());
    final String dirPath = workingDir == null ? null : workingDir.getPath();
    final GeneralCommandLine command = new GeneralCommandLine().withWorkDirectory(dirPath);
    command.setExePath(flutterPath);
    // Example: [create, foo_bar]
    String[] toolArgs = ArrayUtil.mergeArrays(cmd.command, args);
    toolArgs = ArrayUtil.prepend("--no-color", toolArgs);
    command.addParameters(toolArgs);

    try {
      if (inProgress.compareAndSet(false, true)) {
        final OSProcessHandler handler = new OSProcessHandler(command);
        if (listener != null) {
          handler.addProcessListener(listener);
        }
        handler.addProcessListener(new ProcessAdapter() {
          @Override
          public void processTerminated(final ProcessEvent event) {
            inProgress.set(false);
            cmd.onTerminate(module, workingDir, event.getExitCode(), args);
          }
        });

        if (cmd.attachToConsole() && module != null) {
          FlutterConsoleHelper.attach(module, handler, cmd.runOnConsoleActivation());
        }

        cmd.onStart(module, workingDir, args);
        start(handler);

        // Send the command to analytics.
        String commandName = StringUtil.join(cmd.command, "_");
        commandName = commandName.replaceAll("-", "");
        FlutterInitializer.getAnalytics().sendEvent("flutter", commandName);
      }
    }
    catch (ExecutionException e) {
      inProgress.set(false);
      FlutterMessages.showError(
        cmd.title,
        FlutterBundle.message("flutter.command.exception.message", e.getMessage()));
    }
  }

  public void runProject(@NotNull Project project,
                         @NotNull String title,
                         @NotNull String... args)
    throws ExecutionException {
    final String flutterPath = FlutterSdkUtil.pathToFlutterTool(getHomePath());
    final GeneralCommandLine command = new GeneralCommandLine();
    command.setExePath(flutterPath);
    // Example: [create, foo_bar]
    final String[] toolArgs = ArrayUtil.prepend("--no-color", args);
    command.addParameters(toolArgs);

    try {
      if (inProgress.compareAndSet(false, true)) {
        final OSProcessHandler handler = new OSProcessHandler(command);
        handler.addProcessListener(new ProcessAdapter() {
          @Override
          public void processTerminated(final ProcessEvent event) {
            inProgress.set(false);
            printExitMessage(project, null, event.getExitCode());
          }
        });

        FlutterConsoleHelper.attach(project, handler, () -> start(handler));

        // Send the command to analytics.
        FlutterInitializer.getAnalytics().sendEvent("flutter", args[0]);
      }
    }
    catch (ExecutionException e) {
      inProgress.set(false);
      FlutterMessages.showError(
        title,
        FlutterBundle.message("flutter.command.exception.message", e.getMessage()));
    }
  }

  @NotNull
  public String getHomePath() {
    return myHomePath;
  }

  /**
   * Returns the Flutter Version as captured in the VERSION file.  This version is very coarse grained and not meant for presentation and
   * rather only for sanity-checking the presence of baseline features (e.g, hot-reload).
   */
  @NotNull
  public FlutterSdkVersion getVersion() {
    return myVersion;
  }

  @NotNull
  public String getDartSdkPath() throws ExecutionException {
    return FlutterSdkUtil.pathToDartSdk(getHomePath());
  }

  public enum Command {

    CREATE("Flutter create", "create") {
      @Override
      void onStart(@Nullable Module module, @Nullable VirtualFile workingDir, @NotNull String... args) {
        // Enable Dart.
        if (module != null) {
          ApplicationManager.getApplication()
            .invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> DartPlugin.enableDartSdk(module)));
        }
      }

      @Override
      @SuppressWarnings("UnusedParameters")
      void onTerminate(@Nullable Module module,
                       @Nullable VirtualFile workingDir,
                       int exitCode,
                       @NotNull String... args) {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (workingDir != null && module != null && !module.isDisposed()) {
            final Project project = module.getProject();
            final FileEditorManager manager = FileEditorManager.getInstance(project);

            // Find main.
            final VirtualFile main = LocalFileSystem.getInstance().refreshAndFindFileByPath(workingDir.getPath() + "/lib/main.dart");

            // Create a basic run configuration.
            final ConfigurationFactory[] factories = FlutterRunConfigurationType.getInstance().getConfigurationFactories();
            final Optional<ConfigurationFactory> factory =
              Arrays.stream(factories).filter((f) -> f instanceof FlutterRunConfigurationType.FlutterConfigurationFactory).findFirst();
            assert (factory.isPresent());
            final ConfigurationFactory configurationFactory = factory.get();

            final RunManager runManager = RunManager.getInstance(project);
            final List<RunConfiguration> configurations = runManager.getConfigurationsList(FlutterRunConfigurationType.getInstance());

            // If the target project has no flutter run configurations, create one.
            if (configurations.isEmpty()) {
              final RunnerAndConfigurationSettings settings =
                runManager.createRunConfiguration(project.getName(), configurationFactory);
              final FlutterRunConfiguration configuration = (FlutterRunConfiguration)settings.getConfiguration();

              // Set config name.
              String name = configuration.suggestedName();
              if (name == null) {
                name = project.getName();
              }
              configuration.setName(name);

              // Setup parameters.
              final FlutterRunnerParameters parameters = configuration.getRunnerParameters();

              // Add main if appropriate.
              if (main != null && main.exists()) {
                parameters.setFilePath(main.getPath());
              }

              parameters.setWorkingDirectory(workingDir.getPath());

              runManager.addConfiguration(settings, false);
              runManager.setSelectedConfiguration(settings);
            }

            super.onTerminate(module, workingDir, exitCode, args);

            // Open main for editing.
            if (main != null && main.exists()) {
              manager.openFile(main, true);
            }
            else {
              LOG.warn("Unable to find (and open) created `main` file.");
            }
          }
        });
      }
    },
    DOCTOR("Flutter doctor", "doctor"),
    PACKAGES_GET("Flutter packages get", "packages", "get"),
    PACKAGES_UPGRADE("Flutter packages upgrade", "packages", "upgrade"),
    UPGRADE("Flutter upgrade", "upgrade"),
    VERSION("Flutter version", "--version") {
      @Override
      boolean attachToConsole() {
        return false;
      }
    };

    final public String title;
    final String[] command;

    Command(String title, String... command) {
      this.title = title;
      this.command = command;
    }

    /**
     * Whether to attach to a console view (defaults to true).
     */
    @SuppressWarnings("SameReturnValue")
    boolean attachToConsole() {
      return true;
    }

    /**
     * An (optional) action to perform once the console is active.
     */
    @SuppressWarnings("SameReturnValue")
    @Nullable
    Runnable runOnConsoleActivation() {
      return null;
    }

    /**
     * Invoked on command start (before process spawning).
     *
     * @param module     the target module
     * @param workingDir the working directory for the command
     * @param args       any arguments passed into the command
     */
    @SuppressWarnings("UnusedParameters")
    void onStart(@Nullable Module module, @Nullable VirtualFile workingDir, @NotNull String... args) {
      // Default is a no-op.
    }

    /**
     * Invoked after the command terminates.
     *
     * @param module     the target module
     * @param workingDir the working directory for the command
     * @param exitCode   the command process's exit code
     * @param args       any arguments passed into the command
     */
    @SuppressWarnings("UnusedParameters")
    void onTerminate(@Nullable Module module, @Nullable VirtualFile workingDir, int exitCode, @NotNull String... args) {
      if (module != null) {
        printExitMessage(null, module, exitCode);
      }
    }
  }
}
