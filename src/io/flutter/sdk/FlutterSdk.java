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
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.ApplicationLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import io.flutter.FlutterBundle;
import io.flutter.FlutterProjectComponent;
import io.flutter.console.FlutterConsole;
import io.flutter.run.FlutterRunConfiguration;
import io.flutter.run.FlutterRunConfigurationType;
import io.flutter.run.FlutterRunnerParameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class FlutterSdk {

  public static final String FLUTTER_SDK_GLOBAL_LIB_NAME = "Flutter SDK";
  public static final String GROUP_DISPLAY_ID = "Flutter Command Invocation";
  private static final Logger LOG = Logger.getInstance(FlutterSdk.class);
  private static final AtomicBoolean inProgress = new AtomicBoolean(false);
  private static final Key<CachedValue<FlutterSdk>> CACHED_FLUTTER_SDK_KEY = Key.create("CACHED_FLUTTER_SDK_KEY");
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
   * Returns the same as {@link #getGlobalFlutterSdk()} but much faster
   */
  @Nullable
  public static FlutterSdk getFlutterSdk(@NotNull final Project project) {
    CachedValue<FlutterSdk> cachedValue = project.getUserData(CACHED_FLUTTER_SDK_KEY);

    if (cachedValue == null) {
      cachedValue = CachedValuesManager.getManager(project).createCachedValue(() -> {
        final FlutterSdk sdk = getGlobalFlutterSdk();
        if (sdk == null) {
          return new CachedValueProvider.Result<>(null, FlutterProjectComponent.getProjectRootsModificationTracker(project));
        }

        List<Object> dependencies = new ArrayList<>(3);
        dependencies.add(FlutterProjectComponent.getProjectRootsModificationTracker(project));
        ContainerUtil
          .addIfNotNull(dependencies, LocalFileSystem.getInstance().findFileByPath(FlutterSdkUtil.versionPath(sdk.getHomePath())));
        ContainerUtil.addIfNotNull(dependencies, LocalFileSystem.getInstance().findFileByPath(sdk.getHomePath() + "/bin/flutter"));

        return new CachedValueProvider.Result<>(sdk, ArrayUtil.toObjectArray(dependencies));
      }, false);

      project.putUserData(CACHED_FLUTTER_SDK_KEY, cachedValue);
    }

    return cachedValue.getValue();
  }

  @Nullable
  public static FlutterSdk getGlobalFlutterSdk() {
    return findFlutterSdkAmongGlobalLibs(ApplicationLibraryTable.getApplicationTable().getLibraries());
  }

  @Nullable
  private static FlutterSdk findFlutterSdkAmongGlobalLibs(final Library[] globalLibraries) {
    for (final Library library : globalLibraries) {
      if (FLUTTER_SDK_GLOBAL_LIB_NAME.equals(library.getName())) {
        return getSdkByLibrary(library);
      }
    }

    return null;
  }

  @Nullable
  static FlutterSdk getSdkByLibrary(@NotNull final Library library) {
    final VirtualFile[] roots = library.getFiles(OrderRootType.CLASSES);
    if (roots.length == 1) {
      final VirtualFile flutterSdkRoot = findFlutterSdkRoot(roots[0]);
      if (flutterSdkRoot != null) {
        final String homePath = flutterSdkRoot.getPath();
        final String version = FlutterSdkUtil.getSdkVersion(homePath);
        return new FlutterSdk(homePath, version);
      }
    }

    return null;
  }

  private static VirtualFile findFlutterSdkRoot(VirtualFile dartSdkLibDir) {
    // Navigating up from `bin/cache/dart-sdk/lib/`
    int count = 4;
    VirtualFile parent = dartSdkLibDir;
    do {
      parent = parent.getParent();
    }
    while (parent != null && --count > 0);
    return parent;
  }

  static FlutterSdk forPath(String path) {
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
          FlutterConsole.attach(module, handler, commandPrefix + cmd.title);
        }

        cmd.onStart(module, workingDir, args);

        handler.startNotify();
      }
    }
    catch (ExecutionException e) {
      inProgress.set(false);
      Notifications.Bus.notify(
        new Notification(GROUP_DISPLAY_ID, cmd.title, FlutterBundle.message("flutter.command.exception", e.getMessage()),
                         NotificationType.ERROR));
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

    CREATE("create", "Flutter: Create") {
      @Override
      void onStart(@Nullable Module module, @Nullable VirtualFile workingDir, @NotNull String... args) {
        // Enable Dart.
        ApplicationManager.getApplication().invokeLater(() -> FlutterSdkUtil.enableDartSupport(module));
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
    DOCTOR("doctor", "Flutter: Doctor"),
    VERSION("--version", "Flutter: Version") {
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
