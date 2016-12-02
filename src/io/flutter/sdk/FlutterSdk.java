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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.jetbrains.lang.dart.sdk.DartSdk;
import com.jetbrains.lang.dart.sdk.DartSdkGlobalLibUtil;
import io.flutter.FlutterBundle;
import io.flutter.FlutterErrors;
import io.flutter.console.FlutterConsoleHelper;
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
    return getFlutterSdkByDartSdk(DartSdk.getDartSdk(project));
  }

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

  static FlutterSdk forPath(@NotNull final String path) {
    return FlutterSdkUtil.isFlutterSdkHome(path) ? new FlutterSdk(path) : null;
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
    String[] toolArgs = ArrayUtil.prepend(cmd.command, args);
    toolArgs = ArrayUtil.prepend("--no-color", toolArgs);
    command.addParameters(toolArgs);

    FileDocumentManager.getInstance().saveAllDocuments();

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
            cmd.onTerminate(module, workingDir, args);
          }
        });

        if (cmd.attachToConsole() && module != null) {
          final String commandPrefix = "[" + module.getName() + "] ";
          FlutterConsoleHelper.attach(module, handler);
        }

        cmd.onStart(module, workingDir, args);
        handler.startNotify();
      }
    }
    catch (ExecutionException e) {
      inProgress.set(false);
      FlutterErrors.showError(
        cmd.title,
        FlutterBundle.message("flutter.command.exception.message", e.getMessage()));
    }
  }

  public void runProject(@NotNull Project project,
                         @NotNull String title,
                         @Nullable ProcessListener listener,
                         @NotNull String... args)
    throws ExecutionException {
    final String flutterPath = FlutterSdkUtil.pathToFlutterTool(getHomePath());
    final GeneralCommandLine command = new GeneralCommandLine();
    command.setExePath(flutterPath);
    // Example: [create, foo_bar]
    String[] toolArgs = ArrayUtil.prepend("--no-color", args);
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
          }
        });

        FlutterConsoleHelper.attach(project, handler);
        handler.startNotify();
      }
    }
    catch (ExecutionException e) {
      inProgress.set(false);
      FlutterErrors.showError(
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

    CREATE("create", "Flutter create") {
      @Override
      void onStart(@Nullable Module module, @Nullable VirtualFile workingDir, @NotNull String... args) {
        // Enable Dart.
        if (module != null) {
          ApplicationManager.getApplication()
            .invokeLater(() -> ApplicationManager.getApplication().runWriteAction(() -> DartSdkGlobalLibUtil.enableDartSdk(module)));
        }
      }

      @Override
      void onTerminate(@Nullable Module module,
                       @Nullable VirtualFile workingDir,
                       @SuppressWarnings("UnusedParameters") @NotNull String... args) {
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
              parameters.setCheckedMode(false);

              runManager.addConfiguration(settings, false);
              runManager.setSelectedConfiguration(settings);
            }

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
    DOCTOR("doctor", "Flutter doctor"),
    UPGRADE("upgrade", "Flutter upgrade"),
    VERSION("--version", "Flutter version") {
      @Override
      boolean attachToConsole() {
        return false;
      }
    };

    final String command;
    final String title;

    Command(String command, String title) {
      this.command = command;
      this.title = title;
    }

    /**
     * Whether to attach to a console view (defaults to true).
     */
    @SuppressWarnings("SameReturnValue")
    boolean attachToConsole() {
      return true;
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
     * @param args       any arguments passed into the command
     */
    @SuppressWarnings("UnusedParameters")
    void onTerminate(@Nullable Module module, @Nullable VirtualFile workingDir, @NotNull String... args) {
      // Default is a no-op.
    }

  }
}
