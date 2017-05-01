/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
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
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.content.MessageView;
import com.intellij.util.ArrayUtil;
import com.jetbrains.lang.dart.sdk.DartSdk;
import io.flutter.FlutterBundle;
import io.flutter.FlutterInitializer;
import io.flutter.FlutterMessages;
import io.flutter.FlutterUtils;
import io.flutter.console.FlutterConsoleHelper;
import io.flutter.dart.DartPlugin;
import io.flutter.pub.PubRoot;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

public class FlutterSdk {
  public static final String FLUTTER_SDK_GLOBAL_LIB_NAME = "Flutter SDK";

  private static final String DART_CORE_SUFFIX = "/bin/cache/dart-sdk/lib/core";

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
   * Return the FlutterSdk for the given project.
   * <p>
   * Returns null if the Dart SDK is not set or does not exist.
   */
  @Nullable
  public static FlutterSdk getFlutterSdk(@NotNull final Project project) {
    if (project.isDisposed()) {
      return null;
    }
    final DartSdk dartSdk = DartPlugin.getDartSdk(project);
    if (dartSdk == null) {
      return null;
    }

    final String dartPath = dartSdk.getHomePath();
    final String suffix = "/bin/cache/dart-sdk";
    if (!dartPath.endsWith(suffix)) {
      return null;
    }
    return forPath(dartPath.substring(0, dartPath.length() - suffix.length()));
  }

  /**
   * Returns the Flutter SDK for a project that has a possibly broken "Dart SDK" project library.
   * <p>
   * (This can happen for a newly-cloned Flutter SDK where the Dart SDK is not cached yet.)
   */
  @Nullable
  public static FlutterSdk getIncomplete(@NotNull final Project project) {
    if (project.isDisposed()) {
      return null;
    }
    final Library lib = getDartSdkLibrary(project);
    if (lib == null) {
      return null;
    }
    return getFlutterFromDartSdkLibrary(lib, project.getBaseDir());
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

  private static void startListening(@NotNull OSProcessHandler handler) {
    // TODO(skybrian) what is this for? We are only setting it very briefly.
    DartPlugin.setPubActionInProgress(true);
    try {
      handler.startNotify();
    }
    finally {
      DartPlugin.setPubActionInProgress(false);
    }
  }

  /**
   * Runs "flutter --version" and waits for it to complete.
   * <p>
   * This ensures that the Dart SDK exists and is up to date.
   * <p>
   * If project is not null, displays output in a console.
   *
   * @return true if successful (the Dart SDK exists).
   */
  public boolean sync(@Nullable Project project) {
    try {
      final Process process = startProcessWithoutModule(project, "flutter --version", "--version");
      if (process == null) {
        return false;
      }
      process.waitFor();
      if (process.exitValue() != 0) {
        return false;
      }
      final VirtualFile flutterBin = LocalFileSystem.getInstance().findFileByPath(myHomePath + "/bin");
      if (flutterBin == null) {
        return false;
      }
      flutterBin.refresh(false, true);
      return flutterBin.findFileByRelativePath("cache/dart-sdk") != null;
    }
    catch (ExecutionException | InterruptedException e) {
      LOG.warn(e);
      return false;
    }
  }

  /**
   * Starts running 'flutter packages get' on the given pub root provided it's in one of this project's modules.
   * <p>
   * Shows output in the console associated with the given module.
   * <p>
   * Returns the process if successfully started.
   */
  public Process startPackagesGet(@NotNull PubRoot root, @NotNull Project project) throws ExecutionException {
    final Module module = root.getModule(project);
    if (module == null) return null;

    return startProcess(Command.PACKAGES_GET, module, root.getRoot(), new ProcessAdapter() {
      @Override
      public void processTerminated(ProcessEvent event) {
        // Refresh to ensure Dart Plugin sees .packages and doesn't mistakenly nag to run pub.
        root.refresh();
      }
    });
  }

  /**
   * Starts running 'flutter packages upgrade' on the given pub root.
   * <p>
   * Shows output in the console associated with the given module.
   * <p>
   * Returns the process if successfully started.
   */
  public Process startPackagesUpgrade(@NotNull PubRoot root, @NotNull Project project) throws ExecutionException {
    final Module module = root.getModule(project);
    if (module == null) return null;

    return startProcess(Command.PACKAGES_UPGRADE, module, root.getRoot(), new ProcessAdapter() {
      @Override
      public void processTerminated(ProcessEvent event) {
        root.refresh();
      }
    });
  }

  /**
   * Starts running a flutter command.
   * <p>
   * If a module is supplied, shows output in the appropriate console for that module.
   * <p>
   * Returns the process if successfully started.
   * <p>
   * Doesn't run the comand if another command is already running.
   */
  @Nullable
  public Process startProcess(@NotNull Command cmd,
                              @Nullable Module module,
                              @Nullable VirtualFile workingDir,
                              @Nullable ProcessListener listener,
                              @NotNull String... args)
    throws ExecutionException {
    final String flutterPath = FlutterSdkUtil.pathToFlutterTool(getHomePath());
    final String dirPath = workingDir == null ? null : workingDir.getPath();
    final GeneralCommandLine command = new GeneralCommandLine().withWorkDirectory(dirPath);
    command.setCharset(CharsetToolkit.UTF8_CHARSET);
    command.setExePath(flutterPath);
    command.withEnvironment(FlutterSdkUtil.FLUTTER_HOST_ENV, FlutterSdkUtil.getFlutterHostEnvValue());
    // Example: [create, foo_bar]
    String[] toolArgs = ArrayUtil.mergeArrays(cmd.command, args);
    toolArgs = ArrayUtil.prepend("--no-color", toolArgs);
    command.addParameters(toolArgs);

    try {
      if (!inProgress.compareAndSet(false, true)) {
        return null; // already running.
      }
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
      startListening(handler);

      // Send the command to analytics.
      String commandName = StringUtil.join(cmd.command, "_");
      commandName = commandName.replaceAll("-", "");
      FlutterInitializer.getAnalytics().sendEvent("flutter", commandName);
      return handler.getProcess();
    }
    catch (ExecutionException e) {
      inProgress.set(false);
      FlutterMessages.showError(
        cmd.title,
        FlutterBundle.message("flutter.command.exception.message", e.getMessage()));
      return null;
    }
  }

  /**
   * Starts running a flutter command that's not associated with any particular module's console.
   * <p>
   * If a project is supplied, shows output in a console.
   * <p>
   * Returns the process if successfully started.
   * <p>
   * Doesn't run the comand if another command is already running.
   */
  @Nullable
  public Process startProcessWithoutModule(@Nullable Project project,
                                           @NotNull String title,
                                           @NotNull String... args)
    throws ExecutionException {
    final String flutterPath = FlutterSdkUtil.pathToFlutterTool(getHomePath());
    final GeneralCommandLine command = new GeneralCommandLine();
    command.setCharset(CharsetToolkit.UTF8_CHARSET);
    command.setExePath(flutterPath);
    command.withEnvironment(FlutterSdkUtil.FLUTTER_HOST_ENV, FlutterSdkUtil.getFlutterHostEnvValue());
    // Example: [create, foo_bar]
    final String[] toolArgs = ArrayUtil.prepend("--no-color", args);
    command.addParameters(toolArgs);

    if (!inProgress.compareAndSet(false, true)) {
      return null;
    }

    try {
      final OSProcessHandler handler = new OSProcessHandler(command);
      handler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(final ProcessEvent event) {
          inProgress.set(false);
          printExitMessage(project, null, event.getExitCode());
        }
      });

      if (project != null) {
        ApplicationManager.getApplication().invokeAndWait(
          () -> FlutterConsoleHelper.attach(project, handler, () -> startListening(handler)));
      }

      // Send the command to analytics.
      FlutterInitializer.getAnalytics().sendEvent("flutter", args[0]);
      return handler.getProcess();
    }
    catch (ExecutionException e) {
      inProgress.set(false);
      FlutterMessages.showError(
        title,
        FlutterBundle.message("flutter.command.exception.message", e.getMessage()));
      return null;
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

  /**
   * Returns the path to the Dart SDK cached within the Flutter SDK, or null if it doesn't exist.
   */
  @Nullable
  public String getDartSdkPath() {
    return FlutterSdkUtil.pathToDartSdk(getHomePath());
  }

  @Nullable
  private static Library getDartSdkLibrary(@NotNull Project project) {
    final Library[] libraries = ProjectLibraryTable.getInstance(project).getLibraries();
    for (Library lib : libraries) {
      if ("Dart SDK".equals(lib.getName())) {
        return lib;
      }
    }
    return null;
  }

  @Nullable
  private static FlutterSdk getFlutterFromDartSdkLibrary(Library lib, VirtualFile projectDir) {
    final String[] urls = lib.getUrls(OrderRootType.CLASSES);
    for (String url : urls) {
      if (url.endsWith(DART_CORE_SUFFIX)) {
        final String flutterUrl = url.substring(0, url.length() - DART_CORE_SUFFIX.length());
        final VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(flutterUrl);
        return file == null ? null : new FlutterSdk(file.getPath());
      }
    }
    return null;
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
