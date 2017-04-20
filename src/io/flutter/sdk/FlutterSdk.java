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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.JDOMUtil;
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
import io.flutter.utils.FlutterModuleUtils;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class FlutterSdk {
  public static final String FLUTTER_SDK_GLOBAL_LIB_NAME = "Flutter SDK";

  private static final String SDK_LIBRARY_PATH = ".idea/libraries/Dart_SDK.xml";
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
   */
  @Nullable
  public static FlutterSdk getFlutterSdk(@NotNull final Project project) {
    return project.isDisposed() ? null : getFlutterSdkByDartSdk(DartPlugin.getDartSdk(project));
  }

  /**
   * Returns the Flutter SDK for an unopened project that has a possibly broken "Dart SDK" project library.
   * <p>
   * (This can happen for a newly-cloned Flutter SDK where the Dart SDK is not cached yet.)
   */
  @Nullable
  public static FlutterSdk getForProjectDir(final VirtualFile projectDir) {
    final Library lib = loadDartSdkLibrary(projectDir);
    if (lib == null) {
      return null;
    }


    final String[] urls = lib.getUrls(OrderRootType.CLASSES);
    for (String url : urls) {
      if (url.endsWith(DART_CORE_SUFFIX)) {
        final String flutterUrl = url.substring(0, url.length() - DART_CORE_SUFFIX.length());
        final String flutterPath = getPathForProjectLibraryUrl(flutterUrl, projectDir);
        return flutterPath == null ? null : new FlutterSdk(flutterPath);
      }
    }
    return null;
  }

  @Nullable
  private static Library loadDartSdkLibrary(@NotNull VirtualFile projectDir) {
    final VirtualFile libPath = projectDir.findFileByRelativePath(SDK_LIBRARY_PATH);
    if (libPath == null) {
      return null;
    }

    try {
      final Element elt = JDOMUtil.load(new File(libPath.getPath()));
      final ProjectLibraryTable table = new ProjectLibraryTable();
      table.readExternal(elt);
      return table.getLibraryByName("Dart SDK");
    }
    catch (JDOMException | IOException e) {
      return null;
    }
  }

  @Nullable
  private static String getPathForProjectLibraryUrl(@NotNull String url, @NotNull VirtualFile projectDir) {
    // Calling IDEA's path macro expansion is infeasible.
    // So, just expand one variable here and hope it works.
    url = url.replace("$PROJECT_DIR$", projectDir.getPath());
    final VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
    return file == null ? null : file.getPath();
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
   * Shows an indefinite progress dialog while it's running.
   * (Must not run in write action.)
   *
   * @return true if successful (the Dart SDK exists).
   */
  public boolean syncShowingProgress(@Nullable Project project) {
    assert !ApplicationManager.getApplication().isWriteAccessAllowed();

    final ProgressManager progress = ProgressManager.getInstance();
    final AtomicBoolean succeeded = new AtomicBoolean(false);

    progress.runProcessWithProgressSynchronously(() -> {
      try {
        progress.getProgressIndicator().setIndeterminate(true);
        final Process process = startProcess(Command.VERSION, null, null, null);
        if (process == null) {
          return;
        }
        process.waitFor();
        if (process.exitValue() != 0) {
          return;
        }
        final VirtualFile flutterBin = LocalFileSystem.getInstance().findFileByPath(myHomePath + "/bin");
        if (flutterBin == null) {
          return;
        }
        flutterBin.refresh(false, true);
        succeeded.set(flutterBin.findFileByRelativePath("cache/dart-sdk") != null);
      }
      catch (ExecutionException | InterruptedException e) {
        LOG.warn(e);
      }
    }, "Building Flutter Tool", false, project);

    return succeeded.get();
  }

  /**
   * Starts running a flutter command.
   * <p>
   * Returns the process if successful.
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

  public void runProject(@NotNull Project project,
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

        FlutterConsoleHelper.attach(project, handler, () -> startListening(handler));

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

  /**
   * Returns true if the Dart SDK has been downloaded for this Flutter SDK.
   */
  public boolean hasDartSdk() {
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(myHomePath + "/bin/cache/dart-sdk");
    if (file == null) {
      return false;
    }
    file.refresh(false, false);
    return file.exists();
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

            FlutterModuleUtils.createRunConfig(project, main);

            super.onTerminate(module, workingDir, exitCode, args);

            // Open main for editing.
            if (FlutterUtils.exists(main)) {
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
