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
import com.sun.corba.se.spi.orbutil.threadpool.Work;
import io.flutter.bazel.Workspace;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.run.MainFile;
import io.flutter.run.bazel.BazelFields;
import io.flutter.run.daemon.RunMode;
import io.flutter.run.test.TestFields;
import io.flutter.utils.ElementIO;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The fields in a Bazel test run configuration, see {@link io.flutter.run.test.TestFields}.
 */
public class BazelTestFields {

  @Nullable private final String testName;
  @Nullable private final String entryFile;
  @Nullable private final String launchScript;
  @Nullable private final String bazelTarget;

  BazelTestFields(@Nullable String testName, @Nullable String entryFile, @Nullable String launchScript, @Nullable String bazelTarget) {
    if (launchScript == null) {
      throw new IllegalArgumentException("launchScript must be non-null");
    }
    else if (testName != null && entryFile == null) {
      throw new IllegalArgumentException("testName must be specified with an entryFile");
    }
    this.testName = testName;
    this.entryFile = entryFile;
    this.launchScript = launchScript;
    this.bazelTarget = bazelTarget;
  }

  /**
   * Copy constructor
   */
  BazelTestFields(@NotNull BazelTestFields template) {
    this(template.testName, template.entryFile, template.launchScript, template.bazelTarget);
  }

  /**
   * Create non-template from template.
   */
  private BazelTestFields(@NotNull final BazelTestFields template, @NotNull final Workspace workspace) {
    this(
      template.testName,
      template.entryFile,
      StringUtil.isEmptyOrSpaces(template.launchScript) ? getLaunchScriptFromWorkspace(workspace) : template.launchScript,
      template.bazelTarget
    );
  }

  private static String getLaunchScriptFromWorkspace(@NotNull final Workspace workspace) {
    String launchScript = workspace.getLaunchScript();
    if (launchScript != null && !launchScript.startsWith("/")) {
      launchScript = workspace.getRoot().getPath() + "/" + launchScript;
    }
    return launchScript;
  }

  /**
   * Creates settings for running tests with the given name within a Dart file.
   */
  @NotNull
  public static BazelTestFields forTestName(@NotNull String testName, @NotNull String path, @NotNull final Workspace workspace) {
    return new BazelTestFields(testName, path, workspace.getLaunchScript(), null);
  }

  /**
   * Creates settings for running all the tests in a Dart file.
   */
  public static BazelTestFields forFile(@NotNull String path, @NotNull final Workspace workspace) {
    return new BazelTestFields(null, path, null, null);
  }

  /**
   * Creates settings for running all the tests in a Bazel target
   */
  public static BazelTestFields forTarget(@NotNull String target, @NotNull final Workspace workspace) {
    return new BazelTestFields(null, null, workspace.getLaunchScript(), target);
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
  public String getLaunchingScript() {
    return launchScript;
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
    return new BazelTestFields(this, workspace);
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
    BazelFields.checkRunnable(project, getEntryFile(), getLaunchingScript(), getBazelTarget());
  }

  /**
   * Starts running the tests.
   */
  @NotNull
  ProcessHandler run(@NotNull final Project project, @NotNull final RunMode mode) throws ExecutionException {
    return new OSProcessHandler(getLaunchCommand(project, mode));
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

    final VirtualFile appDir = MainFile.verify(entryFile, project).get().getAppDir();

    final String launchingScript = getLaunchingScript();
    assert launchingScript != null; // already checked

    Workspace workspace = Workspace.load(project);

    final GeneralCommandLine commandLine = new GeneralCommandLine().withWorkDirectory(workspace.getRoot().getPath());
    commandLine.setCharset(CharsetToolkit.UTF8_CHARSET);
    commandLine.setExePath(FileUtil.toSystemDependentName(workspace.getTestScript()));
    String relativeEntryFilePath = FileUtil.getRelativePath(workspace.getRoot().getPath(), entryFile, '/');
    commandLine.addParameter("--no-color");
    switch (getScope()) {
      case NAME:
        commandLine.addParameter("--name=" + testName);
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
    ElementIO.addOption(element, "launchingScript", launchScript);
    ElementIO.addOption(element, "testName", testName);
    ElementIO.addOption(element, "entryFile", entryFile);
    ElementIO.addOption(element, "bazelTarget", bazelTarget);
  }

  public static BazelTestFields readFrom(Element element) {
    final Map<String, String> options = ElementIO.readOptions(element);

    final String testName = options.get("testName");
    final String entryFile = options.get("entryFile");
    final String launchScript = options.get("launchingScript");
    final String bazelTarget = options.get("bazelTarget");

    try {
      return new BazelTestFields(testName, entryFile, launchScript, bazelTarget);
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
