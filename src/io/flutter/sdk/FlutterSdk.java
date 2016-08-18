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
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.ApplicationLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class FlutterSdk {

  public static final String FLUTTER_SDK_GLOBAL_LIB_NAME = "Flutter SDK";
  private static final String GROUP_DISPLAY_ID = "Flutter Command Invocation";
  private static final AtomicBoolean inProgress = new AtomicBoolean(false);
  private static final String UNKNOWN_VERSION = "unknown";
  private static final Key<CachedValue<FlutterSdk>> CACHED_FLUTTER_SDK_KEY = Key.create("CACHED_FLUTTER_SDK_KEY");
  private final @NotNull String myHomePath;
  private final @NotNull String myVersion;

  private FlutterSdk(@NotNull final String homePath, @NotNull final String version) {
    myHomePath = homePath;
    myVersion = version;
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
  public static FlutterSdk findFlutterSdkAmongGlobalLibs(final Library[] globalLibraries) {
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
        final String version = StringUtil.notNullize(FlutterSdkUtil.getSdkVersion(homePath), UNKNOWN_VERSION);
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
    return parent != null && parent.getName().equals("flutter") ? parent : null;
  }

  public void run(@NotNull Command cmd, @NotNull Module module, @NotNull VirtualFile workingDir, @NotNull String... args)
    throws ExecutionException {
    final String flutterPath = FlutterSdkUtil.pathToFlutterTool(getHomePath());
    final GeneralCommandLine command = new GeneralCommandLine().withWorkDirectory(workingDir.getPath());
    command.setExePath(flutterPath);
    // Example: [create, foo_bar]
    String[] toolArgs = ArrayUtil.prepend(cmd.command, args);
    command.addParameters(toolArgs);

    FileDocumentManager.getInstance().saveAllDocuments();

    try {
      if (inProgress.compareAndSet(false, true)) {
        final OSProcessHandler handler = new OSProcessHandler(command);
        handler.addProcessListener(new ProcessAdapter() {
          @Override
          public void processTerminated(final ProcessEvent event) {
            inProgress.set(false);
            cmd.onTerminate(module, workingDir, args);
          }
        });
        if (cmd.attachToConsole()) {
          FlutterConsole.attach(module.getProject(), handler, cmd.title);
        }

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
   * @return presentable version with revision, like ea7d5bf291
   */
  @NotNull
  public String getVersion() {
    return myVersion;
  }

  public enum Command {

    CREATE("create", "Flutter Create") {
      @Override
      void onTerminate(@NotNull Module module,
                       @NotNull VirtualFile workingDir,
                       @SuppressWarnings("UnusedParameters") @NotNull String... args) {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (!module.isDisposed()) {
            // Enable Dart.
            FlutterSdkUtil.enableDartSupport(module);
            final FileEditorManager manager = FileEditorManager.getInstance(module.getProject());
            // Open main.
            final VirtualFile main = workingDir.findFileByRelativePath("lib/main.dart");
            if (main != null && main.exists()) {
              manager.openFile(main, true);
            }
            else {
              //TODO(pq): log error
            }
          }
        });
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
    boolean attachToConsole() {
      return true;
    }

    /**
     * Invoked after the command terminates.
     *
     * @param module     the target module
     * @param workingDir the working directory for the command
     * @param args       any arguments passed into the command
     */
    @SuppressWarnings("UnusedParameters")
    void onTerminate(@NotNull Module module, @NotNull VirtualFile workingDir, @NotNull String... args) {
      // Default is a no-op.
    }

  }
}
