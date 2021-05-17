/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import static java.util.Arrays.asList;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.intellij.execution.process.ColoredProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.ui.EdtInvocationManager;
import com.jetbrains.lang.dart.sdk.DartSdk;
import git4idea.config.GitExecutableManager;
import io.flutter.FlutterBundle;
import io.flutter.FlutterUtils;
import io.flutter.dart.DartPlugin;
import io.flutter.module.FlutterProjectType;
import io.flutter.pub.PubRoot;
import io.flutter.run.FlutterDevice;
import io.flutter.run.FlutterLaunchMode;
import io.flutter.run.common.RunMode;
import io.flutter.run.test.TestFields;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.JsonUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.*;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterSdk {
  public static final String FLUTTER_SDK_GLOBAL_LIB_NAME = "Flutter SDK";

  public static final String DART_SDK_SUFFIX = "/bin/cache/dart-sdk";
  public static final String LINUX_DART_SUFFIX = "/google-dartlang";
  public static final String LOCAL_DART_SUFFIX = "/google-dartlang-local";
  public static final String MAC_DART_SUFFIX = "/dart_lang/macos_sdk";

  private static final String DART_CORE_SUFFIX = DART_SDK_SUFFIX + "/lib/core";

  private static final Logger LOG = Logger.getInstance(FlutterSdk.class);

  private static final Map<String, FlutterSdk> projectSdkCache = new HashMap<>();

  private final @NotNull VirtualFile myHome;
  private final @NotNull FlutterSdkVersion myVersion;
  private final Map<String, String> cachedConfigValues = new HashMap<>();

  private FlutterSdk(@NotNull final VirtualFile home, @NotNull final FlutterSdkVersion version) {
    myHome = home;
    myVersion = version;
  }

  public boolean isOlderThanToolsStamp(@NotNull VirtualFile gen) {
    final VirtualFile bin = myHome.findChild("bin");
    if (bin == null) return false;
    final VirtualFile cache = bin.findChild("cache");
    if (cache == null) return false;
    final VirtualFile stamp = cache.findChild("flutter_tools.stamp");
    if (stamp == null) return false;
    try {
      final FileTime genFile = Files.getLastModifiedTime(Paths.get(gen.getPath()));
      final FileTime stampFile = Files.getLastModifiedTime(Paths.get(stamp.getPath()));
      return genFile.compareTo(stampFile) > 0;
    } catch (IOException ignored) {
      return false;
    }
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
    if (!dartPath.endsWith(DART_SDK_SUFFIX)) {
      return null;
    }

    final String sdkPath = dartPath.substring(0, dartPath.length() - DART_SDK_SUFFIX.length());
    return FlutterSdk.forPath(sdkPath);
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
    return getFlutterFromDartSdkLibrary(lib);
  }

  @Nullable
  public static FlutterSdk forPath(@NotNull final String path) {
    final VirtualFile home = LocalFileSystem.getInstance().findFileByPath(path);
    if (home == null || !FlutterSdkUtil.isFlutterSdkHome(path)) {
      return null;
    }
    else {
      return saveSdkInCache(home);
    }
  }

  @NotNull
  private static FlutterSdk saveSdkInCache(VirtualFile home) {
    String cacheKey = home.getCanonicalPath();
    synchronized (projectSdkCache) {
      if (!projectSdkCache.containsKey(cacheKey)) {
        projectSdkCache.put(cacheKey, new FlutterSdk(home, FlutterSdkVersion.readFromSdk(home)));
      }
    }
    return projectSdkCache.get(cacheKey);
  }

  @Nullable
  private static Library getDartSdkLibrary(@NotNull Project project) {
    final LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
    for (Library lib : libraryTable.getLibraries()) {
      if ("Dart SDK".equals(lib.getName())) {
        return lib;
      }
    }
    return null;
  }

  @Nullable
  private static FlutterSdk getFlutterFromDartSdkLibrary(Library lib) {
    final String[] urls = lib.getUrls(OrderRootType.CLASSES);
    for (String url : urls) {
      if (url.endsWith(DART_CORE_SUFFIX)) {
        final String flutterUrl = url.substring(0, url.length() - DART_CORE_SUFFIX.length());
        final VirtualFile home = VirtualFileManager.getInstance().findFileByUrl(flutterUrl);
        return home == null ? null : saveSdkInCache(home);
      }
    }
    return null;
  }

  public FlutterCommand flutterVersion() {
    // TODO(devoncarew): Switch to calling 'flutter --version --machine'. The ouput will look like:
    // Building flutter tool...
    // {
    //   "frameworkVersion": "1.15.4-pre.249",
    //   "channel": "master",
    //   "repositoryUrl": "https://github.com/flutter/flutter",
    //   "frameworkRevision": "3551a51df48743ebd4faa91cc5e3d23db645bdce",
    //   "frameworkCommitDate": "2020-03-03 08:19:06 +0800",
    //   "engineRevision": "5e474ee860a3dfa5970a6c54b1cb584152f9c86f",
    //   "dartSdkVersion": "2.8.0 (build 2.8.0-dev.10.0 fbe9f6115d)"
    // }

    return new FlutterCommand(this, getHome(), FlutterCommand.Type.VERSION);
  }

  public FlutterCommand flutterUpgrade() {
    return new FlutterCommand(this, getHome(), FlutterCommand.Type.UPGRADE);
  }

  public FlutterCommand flutterClean(@NotNull PubRoot root) {
    return new FlutterCommand(this, root.getRoot(), FlutterCommand.Type.CLEAN);
  }

  public FlutterCommand flutterDoctor() {
    return new FlutterCommand(this, getHome(), FlutterCommand.Type.DOCTOR);
  }

  @NonNls
  public FlutterCommand flutterCreate(@NotNull VirtualFile appDir, @Nullable FlutterCreateAdditionalSettings additionalSettings) {
    final List<String> args = new ArrayList<>();
    if (additionalSettings != null) {
      args.addAll(additionalSettings.getArgs());
      if (FlutterProjectType.PLUGIN.equals(additionalSettings.getType()) && getVersion().flutterCreateSupportsPlatforms()) {
        // TODO(messick): Remove this after the wizard UI is updated.
        if (!args.contains("--platforms")) {
          args.add("--platforms");
          args.add("android,ios");
        }
      }
    }

    // keep as the last argument
    args.add(appDir.getName());

    final String[] vargs = args.toArray(new String[0]);

    return new FlutterCommand(this, appDir.getParent(), FlutterCommand.Type.CREATE, vargs);
  }

  public FlutterCommand flutterPackagesGet(@NotNull PubRoot root) {
    return new FlutterCommand(this, root.getRoot(), FlutterCommand.Type.PUB_GET);
  }

  public FlutterCommand flutterPackagesUpgrade(@NotNull PubRoot root) {
    return new FlutterCommand(this, root.getRoot(), FlutterCommand.Type.PUB_UPGRADE);
  }

  public FlutterCommand flutterPackagesOutdated(@NotNull PubRoot root) {
    return new FlutterCommand(this, root.getRoot(), FlutterCommand.Type.PUB_OUTDATED);
  }

  public FlutterCommand flutterPub(@Nullable PubRoot root, String... args) {
    return new FlutterCommand(this, root == null ? null : root.getRoot(), FlutterCommand.Type.PUB, args);
  }

  public FlutterCommand flutterBuild(@NotNull PubRoot root, String... additionalArgs) {
    return new FlutterCommand(this, root.getRoot(), FlutterCommand.Type.BUILD, additionalArgs);
  }

  public FlutterCommand flutterConfig(String... additionalArgs) {
    return new FlutterCommand(this, getHome(), FlutterCommand.Type.CONFIG, additionalArgs);
  }

  public FlutterCommand flutterChannel() {
    return new FlutterCommand(this, getHome(), FlutterCommand.Type.CHANNEL);
  }

  public FlutterCommand flutterRun(@NotNull PubRoot root,
                                   @NotNull VirtualFile main,
                                   @NotNull FlutterDevice device,
                                   @NotNull RunMode mode,
                                   @NotNull FlutterLaunchMode flutterLaunchMode,
                                   @NotNull Project project,
                                   String... additionalArgs) {
    final List<String> args = new ArrayList<>();
    args.add("--machine");
    if (FlutterSettings.getInstance().isVerboseLogging()) {
      args.add("--verbose");
    }

    if (flutterLaunchMode == FlutterLaunchMode.DEBUG) {
      if (getVersion().isTrackWidgetCreationRecommended()) {
        // Ensure additionalArgs doesn't have any arg like 'track-widget-creation'.
        if (Arrays.stream(additionalArgs).noneMatch(s -> s.contains("track-widget-creation"))) {
          args.add("--track-widget-creation");
        }
      }
    }

    args.add("--device-id=" + device.deviceId());

    // TODO (helin24): Remove special handling for web-server if we can fix https://github.com/flutter/flutter-intellij/issues/4767.
    // Currently we can't connect to the VM service for the web-server 'device' to resume.
    if (mode == RunMode.DEBUG || (mode == RunMode.RUN && !device.deviceId().equals("web-server"))) {
      args.add("--start-paused");
    }

    if (flutterLaunchMode == FlutterLaunchMode.PROFILE) {
      args.add("--profile");
    }
    else if (flutterLaunchMode == FlutterLaunchMode.RELEASE) {
      args.add("--release");
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

  public FlutterCommand flutterAttach(@NotNull PubRoot root, @NotNull VirtualFile main, @Nullable FlutterDevice device,
                                      @NotNull FlutterLaunchMode flutterLaunchMode, String... additionalArgs) {
    final List<String> args = new ArrayList<>();
    args.add("--machine");
    if (FlutterSettings.getInstance().isVerboseLogging()) {
      args.add("--verbose");
    }

    // TODO(messick): Check that 'flutter attach' supports these arguments.
    if (flutterLaunchMode == FlutterLaunchMode.PROFILE) {
      args.add("--profile");
    }
    else if (flutterLaunchMode == FlutterLaunchMode.RELEASE) {
      args.add("--release");
    }

    if (device != null) {
      args.add("--device-id=" + device.deviceId());
    }

    // TODO(messick): Add others (target, debug-port).
    args.addAll(asList(additionalArgs));

    // Make the path to main relative (to make the command line prettier).
    final String mainPath = root.getRelativePath(main);
    if (mainPath == null) {
      throw new IllegalArgumentException("main isn't within the pub root: " + main.getPath());
    }
    args.add(FileUtil.toSystemDependentName(mainPath));

    return new FlutterCommand(this, root.getRoot(), FlutterCommand.Type.ATTACH, args.toArray(new String[]{}));
  }

  public FlutterCommand flutterRunOnTester(@NotNull PubRoot root, @NotNull String mainPath) {
    final List<String> args = new ArrayList<>();
    args.add("--machine");
    args.add("--device-id=flutter-tester");
    args.add(mainPath);
    return new FlutterCommand(this, root.getRoot(), FlutterCommand.Type.RUN, args.toArray(new String[]{}));
  }

  public FlutterCommand flutterTest(@NotNull PubRoot root, @NotNull VirtualFile fileOrDir, @Nullable String testNameSubstring,
                                    @NotNull RunMode mode, @Nullable String additionalArgs, TestFields.Scope scope) {

    final List<String> args = new ArrayList<>();
    if (myVersion.flutterTestSupportsMachineMode()) {
      args.add("--machine");
      // Otherwise, just run it normally and show the output in a non-test console.
    }
    if (mode == RunMode.DEBUG) {
      if (!myVersion.flutterTestSupportsMachineMode()) {
        throw new IllegalStateException("Flutter SDK is too old to debug tests");
      }
    }
    // Starting the app paused so the IDE can catch early errors is ideal. However, we don't have a way to resume for multiple test files
    // yet, so we want to exclude directory scope tests from starting paused. See https://github.com/flutter/flutter-intellij/issues/4737.
    if (mode == RunMode.DEBUG || (mode == RunMode.RUN && !scope.equals(TestFields.Scope.DIRECTORY))) {
      args.add("--start-paused");
    }
    if (FlutterSettings.getInstance().isVerboseLogging()) {
      args.add("--verbose");
    }
    if (testNameSubstring != null) {
      if (!myVersion.flutterTestSupportsFiltering()) {
        throw new IllegalStateException("Flutter SDK is too old to select tests by name");
      }
      args.add("--plain-name");
      args.add(testNameSubstring);
    }

    if (additionalArgs != null && !additionalArgs.trim().isEmpty()) {
      args.addAll(Arrays.asList(additionalArgs.trim().split(" ")));
    }

    if (mode == RunMode.COVERAGE) {
      if (!args.contains("--coverage")) {
        args.add("--coverage");
      }
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
   * Runs flutter create and waits for it to finish.
   * <p>
   * Shows output in a console unless the module parameter is null.
   * <p>
   * Notifies process listener if one is specified.
   * <p>
   * Returns the PubRoot if successful.
   */
  @Nullable
  public PubRoot createFiles(@NotNull VirtualFile baseDir, @Nullable Module module, @Nullable ProcessListener listener,
                             @Nullable FlutterCreateAdditionalSettings additionalSettings) {
    final Process process;
    if (module == null) {
      process = flutterCreate(baseDir, additionalSettings).start(null, listener);
    }
    else {
      process = flutterCreate(baseDir, additionalSettings).startInModuleConsole(module, null, listener);
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
      FlutterUtils.warn(LOG, e);
      return null;
    }

    if (EdtInvocationManager.getInstance().isEventDispatchThread()) {
      VfsUtil.markDirtyAndRefresh(false, true, true, baseDir); // Need this for AS.
    }
    else {
      baseDir.refresh(false, true); // The current thread must NOT be in a read action.
    }
    return PubRoot.forDirectory(baseDir);
  }

  /**
   * Starts running 'flutter pub get' on the given pub root provided it's in one of this project's modules.
   * <p>
   * Shows output in the console associated with the given module.
   * <p>
   * Returns the process if successfully started.
   */
  public Process startPubGet(@NotNull PubRoot root, @NotNull Project project) {
    final Module module = root.getModule(project);
    if (module == null) return null;
    // Ensure pubspec is saved.
    FileDocumentManager.getInstance().saveAllDocuments();
    // Refresh afterwards to ensure Dart Plugin sees .packages and doesn't mistakenly nag to run pub.
    return flutterPackagesGet(root).startInModuleConsole(module, root::refresh, null);
  }

  /**
   * Starts running 'flutter pub upgrade' on the given pub root.
   * <p>
   * Shows output in the console associated with the given module.
   * <p>
   * Returns the process if successfully started.
   */
  public Process startPubUpgrade(@NotNull PubRoot root, @NotNull Project project) {
    final Module module = root.getModule(project);
    if (module == null) return null;
    // Ensure pubspec is saved.
    FileDocumentManager.getInstance().saveAllDocuments();
    return flutterPackagesUpgrade(root).startInModuleConsole(module, root::refresh, null);
  }

  /**
   * Starts running 'flutter pub outdated' on the given pub root.
   * <p>
   * Shows output in the console associated with the given module.
   * <p>
   * Returns the process if successfully started.
   */
  public Process startPubOutdated(@NotNull PubRoot root, @NotNull Project project) {
    final Module module = root.getModule(project);
    if (module == null) return null;
    // Ensure pubspec is saved.
    FileDocumentManager.getInstance().saveAllDocuments();
    return flutterPackagesOutdated(root).startInModuleConsole(module, root::refresh, null);
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
   * Returns the Flutter Version as captured in the 'version' file. This version is very coarse grained and not meant for presentation and
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
  @NonNls
  public FlutterSdkChannel queryFlutterChannel(boolean useCachedValue) {
    if (useCachedValue) {
      String channel = cachedConfigValues.get("channel");
      if (channel != null) {
        return FlutterSdkChannel.fromText(channel);
      }
    }

    VirtualFile dir = LocalFileSystem.getInstance().findFileByPath(getHomePath());
    assert dir != null;
    String branch;
    try {
      branch = git4idea.light.LightGitUtilKt.getLocation(dir, GitExecutableManager.getInstance().getExecutable((Project)null));
    }
    catch (VcsException e) {
      String stdout = returnOutputOfQuery(flutterChannel());
      if (stdout == null) {
        branch = "unknown";
      }
      else {
        branch = FlutterSdkChannel.parseChannel(stdout);
      }
    }

    cachedConfigValues.put("channel", branch);
    return FlutterSdkChannel.fromText(branch);
  }

  @NonNls
  private static final String[] PLATFORMS =
    new String[]{"enable-android", "enable-ios", "enable-web", "enable-linux-desktop", "enable-macos-desktop", "enable-windows-desktop"};

  @NotNull
  @NonNls
  public Set<String> queryConfiguredPlatforms(boolean useCachedValue) {
    Set<String> platforms = new HashSet<>();
    // Someone could do: flutter config --no-enable-ios --no-enable-android
    platforms.add("enable-android");
    platforms.add("enable-ios");
    if (useCachedValue) {
      for (String key : PLATFORMS) {
        String value = cachedConfigValues.get(key);
        if ("true".equals(value)) {
          platforms.add(key);
        }
        else if ("false".equals(value)) {
          platforms.remove(key);
        }
      }
      return platforms;
    }

    String stdout = returnOutputOfQuery(flutterConfig("--machine"));
    if (stdout == null) {
      return platforms;
    }
    try {
      final JsonElement elem = JsonUtils.parseString(stdout.substring(stdout.indexOf('{')));
      if (elem.isJsonNull()) {
        FlutterUtils.warn(LOG, FlutterBundle.message("flutter.sdk.invalid.json.error"));
        return platforms;
      }

      final JsonObject obj = elem.getAsJsonObject();
      for (String key : PLATFORMS) {
        final JsonPrimitive primitive = obj.getAsJsonPrimitive(key);
        if (primitive != null) {
          if ("true".equals(primitive.getAsString())) {
            platforms.add(key);
          }
          else if ("false".equals(primitive.getAsString())) {
            platforms.remove(key);
          }
          cachedConfigValues.put(key, primitive.getAsString());
        }
      }
    }
    catch (JsonSyntaxException ignored) {
    }
    return platforms;
  }

  /**
   * Query 'flutter config' for the given key, and optionally use any existing cached value.
   */
  @Nullable
  public String queryFlutterConfig(String key, boolean useCachedValue) {
    if (useCachedValue && cachedConfigValues.containsKey(key)) {
      return cachedConfigValues.get(key);
    }

    String stdout = returnOutputOfQuery(flutterConfig("--machine"));
    if (stdout != null) {
      try {
        final JsonElement elem = JsonUtils.parseString(stdout.substring(stdout.indexOf('{')));
        if (elem.isJsonNull()) {
          FlutterUtils.warn(LOG, FlutterBundle.message("flutter.sdk.invalid.json.error"));
          return null;
        }

        final JsonObject obj = elem.getAsJsonObject();
        for (String jsonKey : JsonUtils.getKeySet(obj)) {
          final JsonPrimitive primitive = obj.getAsJsonPrimitive(jsonKey);
          if (primitive != null) {
            cachedConfigValues.put(jsonKey, primitive.getAsString());
          }
        }
      }
      catch (JsonSyntaxException ignored) {
      }
    }
    return null;
  }

  // Do not run this on EDT.
  @Nullable
  private String returnOutputOfQuery(@NotNull FlutterCommand command) {
    final ColoredProcessHandler process = command.startProcess(false);
    if (process == null) {
      return null;
    }

    final StringBuilder stdout = new StringBuilder();
    process.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        if (outputType == ProcessOutputTypes.STDOUT) {
          stdout.append(event.getText());
        }
      }
    });

    LOG.info("Calling " + command.getDisplayCommand());
    final long start = System.currentTimeMillis();
    process.startNotify();
    if (process.waitFor(5000)) {
      final long duration = System.currentTimeMillis() - start;
      LOG.info(command.getDisplayCommand() + ": " + duration + "ms");
      final Integer code = process.getExitCode();
      if (code != null && code == 0) {
        return stdout.toString();
      }
      else {
        LOG.info("Exit code from " + command.getDisplayCommand() + ": " + code);
      }
    }
    else {
      LOG.info("Timeout when calling " + command.getDisplayCommand());
    }
    return null;
  }
}
