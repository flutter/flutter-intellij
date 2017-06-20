/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.execution.process.ProcessListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.jetbrains.lang.dart.sdk.DartSdk;
import io.flutter.FlutterInitializer;
import io.flutter.dart.DartPlugin;
import io.flutter.pub.PubRoot;
import io.flutter.run.daemon.FlutterDevice;
import io.flutter.run.daemon.RunMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;

public class FlutterSdk {
  public static final String FLUTTER_SDK_GLOBAL_LIB_NAME = "Flutter SDK";

  private static final String DART_CORE_SUFFIX = "/bin/cache/dart-sdk/lib/core";

  private static final Logger LOG = Logger.getInstance(FlutterSdk.class);

  private final @NotNull VirtualFile myHome;
  private final @NotNull FlutterSdkVersion myVersion;

  private FlutterSdk(@NotNull final VirtualFile home, @NotNull final FlutterSdkVersion version) {
    myHome = home;
    myVersion = version;
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

  @Nullable
  public static FlutterSdk forPath(@NotNull final String path) {
    final VirtualFile home = LocalFileSystem.getInstance().findFileByPath(path);
    if (home == null || !FlutterSdkUtil.isFlutterSdkHome(path)) {
      return null;
    } else {
      return new FlutterSdk(home, FlutterSdkVersion.readFromSdk(home));
    }
  }

  public FlutterCommand flutterVersion() {
    return new FlutterCommand(this, getHome(), FlutterCommand.Type.VERSION);
  }

  public FlutterCommand flutterUpgrade() {
    return new FlutterCommand(this, getHome(), FlutterCommand.Type.UPGRADE);
  }

  public FlutterCommand flutterDoctor() {
    return new FlutterCommand(this, getHome(), FlutterCommand.Type.DOCTOR);
  }

  public FlutterCommand flutterCreate(@NotNull VirtualFile appDir) {
    return new FlutterCommand(this, appDir.getParent(), FlutterCommand.Type.CREATE, appDir.getName());
  }

  public FlutterCommand flutterPackagesGet(@NotNull PubRoot root) {
    return new FlutterCommand(this, root.getRoot(), FlutterCommand.Type.PACKAGES_GET);
  }

  public FlutterCommand flutterPackagesUpgrade(@NotNull PubRoot root) {
    return new FlutterCommand(this, root.getRoot(), FlutterCommand.Type.PACKAGES_UPGRADE);
  }

  public FlutterCommand flutterRun(@NotNull PubRoot root, @NotNull VirtualFile main,
                                   @Nullable FlutterDevice device, @NotNull RunMode mode, String... additionalArgs) {
    final List<String> args = new ArrayList<>();
    args.add("--machine");
    if (FlutterInitializer.isVerboseLogging()) {
      args.add("--verbose");
    }
    if (device != null) {
      args.add("--device-id=" + device.deviceId());
    }
    if (mode == RunMode.DEBUG) {
      args.add("--start-paused");
    }
    if (!mode.isReloadEnabled()) {
      args.add("--no-hot");
    }
    args.addAll(asList(additionalArgs));

    // Make the path to main relative (to make the command line prettier).
    final String mainPath = root.getRelativePath(main);
    if (mainPath == null) {
      throw new IllegalArgumentException("main isn't within the pub root: " + main.getPath());
    }
    args.add(FileUtil.toSystemDependentName(mainPath));

    return new FlutterCommand(this, root.getRoot(), FlutterCommand.Type.RUN, args.toArray(new String[]{}));
  }

  public FlutterCommand flutterTest(@NotNull PubRoot root, @NotNull VirtualFile fileOrDir, @NotNull RunMode mode) {

    final List<String> args = new ArrayList<>();
    if (myVersion.flutterTestSupportsMachineMode()) {
      args.add("--machine");
      // Otherwise, just run it normally and show the output in a non-test console.
    }
    if (mode == RunMode.DEBUG) {
      if (!myVersion.flutterTestSupportsMachineMode()) {
        throw new IllegalStateException("Flutter SDK is too old to debug tests");
      }
      args.add("--start-paused");
    }
    if (FlutterInitializer.isVerboseLogging()) {
      args.add("--verbose");
    }

    if (!root.getRoot().equals(fileOrDir)) {
      // Make the path to main relative (to make the command line prettier).
      final String mainPath = root.getRelativePath(fileOrDir);
      if (mainPath == null) {
        throw new IllegalArgumentException("main isn't within the pub root: " + fileOrDir.getPath());
      }
      args.add(FileUtil.toSystemDependentName(mainPath));
    }

    return new FlutterCommand(this, root.getRoot(), FlutterCommand.Type.TEST, args.toArray(new String[]{}));
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
  public boolean sync(@NotNull Project project) {
    try {
      final Process process = flutterVersion().startInConsole(project);
      if (process == null) {
        return false;
      }
      process.waitFor();
      if (process.exitValue() != 0) {
        return false;
      }
      final VirtualFile flutterBin = myHome.findChild("bin");
      if (flutterBin == null) {
        return false;
      }
      flutterBin.refresh(false, true);
      return flutterBin.findFileByRelativePath("cache/dart-sdk") != null;
    }
    catch (InterruptedException e) {
      LOG.warn(e);
      return false;
    }
  }
  
  /**
   * Runs flutter create and waits for it to finish.
   * <p>
   * Shows output in a console unless the module parameter is null.
   * <p>
   * Notifies process listener if one is specified.
   * <p>
   *
   * Returns the PubRoot if successful.
   */
  @Nullable
  public PubRoot createFiles(@NotNull VirtualFile baseDir, @Nullable Module module, @Nullable ProcessListener listener) {

    final Process process;
    if (module == null) {
      process = flutterCreate(baseDir).start(null, listener);
    } else {
      process = flutterCreate(baseDir).startInModuleConsole(module, null, listener);
    }
    if (process == null) {
      return null;
    }

    try {
      if (process.waitFor() != 0) {
        return null;
      }
    }
    catch (InterruptedException e) {
      LOG.warn(e);
      return null;
    }

    baseDir.refresh(false, true);
    return PubRoot.forDirectory(baseDir);
  }
  
  /**
   * Starts running 'flutter packages get' on the given pub root provided it's in one of this project's modules.
   * <p>
   * Shows output in the console associated with the given module.
   * <p>
   * Returns the process if successfully started.
   */
  public Process startPackagesGet(@NotNull PubRoot root, @NotNull Project project) {
    final Module module = root.getModule(project);
    if (module == null) return null;
    // Refresh afterwards to ensure Dart Plugin sees .packages and doesn't mistakenly nag to run pub.
    return flutterPackagesGet(root).startInModuleConsole(module, root::refresh, null);
  }

  /**
   * Starts running 'flutter packages upgrade' on the given pub root.
   * <p>
   * Shows output in the console associated with the given module.
   * <p>
   * Returns the process if successfully started.
   */
  public Process startPackagesUpgrade(@NotNull PubRoot root, @NotNull Project project) {
    final Module module = root.getModule(project);
    if (module == null) return null;
    return flutterPackagesUpgrade(root).startInModuleConsole(module, root::refresh, null);
  }

  @NotNull
  public VirtualFile getHome() {
    return myHome;
  }

  @NotNull
  public String getHomePath() {
    return myHome.getPath();
  }

  /**
   * Returns the Flutter Version as captured in the VERSION file. This version is very coarse grained and not meant for presentation and
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
        final VirtualFile home = VirtualFileManager.getInstance().findFileByUrl(flutterUrl);
        return home == null ? null : new FlutterSdk(home, FlutterSdkVersion.readFromSdk(home));
      }
    }
    return null;
  }
}
