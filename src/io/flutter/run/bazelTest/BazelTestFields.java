/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.sdk.DartConfigurable;
import com.jetbrains.lang.dart.sdk.DartSdk;
import io.flutter.FlutterBundle;
import io.flutter.bazel.Workspace;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.dart.DartPlugin;
import io.flutter.run.MainFile;
import io.flutter.run.daemon.RunMode;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSettingsConfigurable;
import io.flutter.settings.FlutterSettings;
import io.flutter.settings.FlutterUIConfig;
import io.flutter.utils.ElementIO;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The fields in a Bazel test run configuration.
 */
public class BazelTestFields {

  @Nullable private final String testName;
  @Nullable private final String entryFile;
  @Nullable private final String bazelTarget;

  BazelTestFields(@Nullable String testName, @Nullable String entryFile, @Nullable String bazelTarget) {
    if (testName != null && entryFile == null) {
      throw new IllegalArgumentException("testName must be specified with an entryFile");
    }
    this.testName = testName;
    this.entryFile = entryFile;
    this.bazelTarget = bazelTarget;
  }

  /**
   * Copy constructor
   */
  BazelTestFields(@NotNull BazelTestFields template) {
    this(template.testName, template.entryFile, template.bazelTarget);
  }

  private String getTestScriptFromWorkspace(@NotNull final Workspace workspace) {
    final FlutterSettings settings = FlutterSettings.getInstance();

    String testScript = workspace.getTestScript();
    // Fall back on the regular launch script if the test script is not available.
    // Also fall back on the regular launch script if the user has opted out of the new bazel test script.
    if (testScript == null || !settings.useNewBazelTestRunner()) {
      testScript = workspace.getLaunchScript();
    }
    if (testScript != null && !testScript.startsWith("/")) {
      testScript = workspace.getRoot().getPath() + "/" + testScript;
    }
    return testScript;
  }

  /**
   * Creates settings for running tests with the given name within a Dart file.
   */
  @NotNull
  public static BazelTestFields forTestName(@NotNull String testName, @NotNull String path) {
    return new BazelTestFields(testName, path, null);
  }

  /**
   * Creates settings for running all the tests in a Dart file.
   */
  public static BazelTestFields forFile(@NotNull String path) {
    return new BazelTestFields(null, path, null);
  }

  /**
   * Creates settings for running all the tests in a Bazel target
   */
  public static BazelTestFields forTarget(@NotNull String target) {
    return new BazelTestFields(null, null, target);
  }


  @Nullable
  public String getTestName() {
    return testName;
  }

  /**
   * The file containing the main function that starts the Flutter test.
   */
  @Nullable
  public String getEntryFile() {
    return entryFile;
  }

  /**
   * Returns the file or directory containing the tests to run, or null if it doesn't exist.
   */
  @Nullable
  public VirtualFile getFile() {
    final String path = getEntryFile();
    if (path == null) return null;
    return LocalFileSystem.getInstance().findFileByPath(path);
  }

  @Nullable
  public String getBazelTarget() {
    return bazelTarget;
  }

  @NotNull
  BazelTestFields copy() {
    return new BazelTestFields(this);
  }

  @NotNull
  BazelTestFields copyTemplateToNonTemplate(@NotNull final Project project) {
    final Workspace workspace = WorkspaceCache.getInstance(project).getNow();
    if (workspace == null) return new BazelTestFields(this);
    return new BazelTestFields(this);
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

    final FlutterSettings settings = FlutterSettings.getInstance();

    switch (getScope()) {
      case TARGET_PATTERN:
        // check that bazel target is not empty
        if (StringUtil.isEmptyOrSpaces(bazelTarget)) {
          throw new RuntimeConfigurationError(FlutterBundle.message("flutter.run.bazel.noTargetSet"));
        }
        // check that the bazel target starts with "//"
        if (!bazelTarget.startsWith("//")) {
          throw new RuntimeConfigurationError(FlutterBundle.message("flutter.run.bazel.startWithSlashSlash"));
        }
        break;
      case FILE:
      case NAME:
        if (!settings.useNewBazelTestRunner()) {
          throw new RuntimeConfigurationError(FlutterBundle.message("flutter.run.bazel.mustUseNewBazelTestRunner"),
                                              () -> FlutterSettingsConfigurable.openFlutterSettings(project));
        }
        final MainFile.Result main = MainFile.verify(entryFile, project);
        if (!main.canLaunch()) {
          throw new RuntimeConfigurationError(main.getError());
        }
        break;
    }
  }

  /**
   * Starts running the tests.
   */
  @NotNull
  ProcessHandler run(@NotNull final Project project, @NotNull final RunMode mode) throws ExecutionException {
    return new OSProcessHandler(getLaunchCommand(project, mode));
  }

  /**
   * Returns the app directory that corresponds to the entryFile and the given project.
   */
  protected VirtualFile getAppDir(@NotNull Project project) {
    return MainFile.verify(entryFile, project).get().getAppDir();
  }

  /**
   * Returns the command to use to launch the Flutter app. (Via running the Bazel target.)
   */
  @NotNull
  GeneralCommandLine getLaunchCommand(@NotNull final Project project,
                                      @NotNull final RunMode mode)
    throws ExecutionException {
    try {
      checkRunnable(project);
    }
    catch (RuntimeConfigurationError e) {
      throw new ExecutionException(e);
    }

    final VirtualFile appDir = getAppDir(project);
    final Workspace workspace = getWorkspace(project);

    final String launchingScript = getTestScriptFromWorkspace(workspace);
    assert launchingScript != null; // already checked

    final GeneralCommandLine commandLine = new GeneralCommandLine().withWorkDirectory(workspace.getRoot().getPath());
    commandLine.setCharset(CharsetToolkit.UTF8_CHARSET);
    commandLine.setExePath(FileUtil.toSystemDependentName(launchingScript));
    final String relativeEntryFilePath = entryFile == null
        ? null
        : FileUtil.getRelativePath(workspace.getRoot().getPath(), entryFile, '/');
    commandLine.addParameter("--no-color");
    switch (getScope()) {
      case NAME:
        commandLine.addParameters("--name", testName);
        commandLine.addParameter(relativeEntryFilePath);
        break;
      case FILE:
        commandLine.addParameter(relativeEntryFilePath);
        break;
      case TARGET_PATTERN:
        commandLine.addParameter(bazelTarget);
        break;
    }

    if (mode == RunMode.DEBUG) {
      commandLine.addParameters("--", "--enable-debugging");
    }
    return commandLine;
  }

  @Nullable
  protected Workspace getWorkspace(@NotNull Project project) {
    return Workspace.load(project);
  }


  /**
   * Determines the type of test invocation we need to run: test-by-name, test-by-file, or test-by-bazel-target.
   *
   * <p>
   * We can assume the following about this BazelTestFields instance based on the Scope returned.
   *
   * <p>
   * <ul>
   *   <li>Scope.NAME: The testName and entryFile fields are both non-null.  The bazelTarget field may be null.</li>
   *   <li>Scope.FILE: The entryFile field is non-null.  The bazelTarget field may be null.</li>
   *   <li>Scope.TARGET_PATTERN: The testName and entryFile fields may both be null.  If the bazelTarget field is non-null, this target is
   *   runnable.</li>
   * </ul>
   *
   */
  @NotNull
  public Scope getScope() {
    if (testName != null && entryFile != null) {
      return Scope.NAME;
    }
    else if (entryFile != null) {
      return Scope.FILE;
    }
    else {
      return Scope.TARGET_PATTERN;
    }
  }

  public void writeTo(Element element) {
    ElementIO.addOption(element, "testName", testName);
    ElementIO.addOption(element, "entryFile", entryFile);
    ElementIO.addOption(element, "bazelTarget", bazelTarget);
  }

  public static BazelTestFields readFrom(Element element) {
    final Map<String, String> options = ElementIO.readOptions(element);

    final String testName = options.get("testName");
    final String entryFile = options.get("entryFile");
    final String bazelTarget = options.get("bazelTarget");

    try {
      return new BazelTestFields(testName, entryFile, bazelTarget);
    } catch (IllegalArgumentException e) {
      throw new InvalidDataException(e.getMessage());
    }
  }

  public enum Scope {
    NAME("Tests in file, filtered by name") {
      @Override
      public void checkRunnable(@NotNull BazelTestFields fields, @NotNull Project project) throws RuntimeConfigurationError {
        FILE.checkRunnable(fields, project);
      }
    },

    FILE("All tests in a file") {
      @Override
      public void checkRunnable(@NotNull BazelTestFields fields, @NotNull Project project) throws RuntimeConfigurationError {
        final MainFile.Result main = MainFile.verify(fields.entryFile, project);
        if (!main.canLaunch()) {
          throw new RuntimeConfigurationError(main.getError());
        }
      }
    },

    TARGET_PATTERN("All tests in a bazel target or matching a bazel target pattern") {
      @Override
      public void checkRunnable(@NotNull BazelTestFields fields, @NotNull Project project) {}
    };

    private final String displayName;

    Scope(String displayName) {
      this.displayName = displayName;
    }

    public String getDisplayName() {
      return displayName;
    }

    public abstract void checkRunnable(@NotNull BazelTestFields fields, @NotNull Project project) throws RuntimeConfigurationError;
  }
}
