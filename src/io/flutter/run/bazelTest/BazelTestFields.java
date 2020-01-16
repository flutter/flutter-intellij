/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineTokenizer;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RuntimeConfigurationError;
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
import io.flutter.run.bazelTest.FlutterBazelTestConfigurationType.WatchFactory;
import io.flutter.run.common.RunMode;
import io.flutter.sdk.FlutterSettingsConfigurable;
import io.flutter.utils.ElementIO;
import io.flutter.utils.MostlySilentOsProcessHandler;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The fields in a Bazel test run configuration.
 */
public class BazelTestFields {
  @Nullable private final String testName;
  @Nullable private final String entryFile;
  @Nullable private final String bazelTarget;
  @Nullable private final String additionalArgs;

  BazelTestFields(@Nullable String testName, @Nullable String entryFile, @Nullable String bazelTarget, @Nullable String additionalArgs) {
    if (testName != null && entryFile == null) {
      throw new IllegalArgumentException("testName must be specified with an entryFile");
    }
    this.testName = testName;
    this.entryFile = entryFile;
    this.bazelTarget = bazelTarget;
    this.additionalArgs = additionalArgs;
  }

  /**
   * Copy constructor
   */
  BazelTestFields(@NotNull BazelTestFields template) {
    this(template.testName, template.entryFile, template.bazelTarget, template.additionalArgs);
  }

  private String getTestScriptFromWorkspace(@NotNull Project project) {
    final Workspace workspace = getWorkspace(project);
    String testScript = workspace.getTestScript();
    // Fall back on the regular launch script if the test script is not available.
    if (testScript == null) {
      testScript = workspace.getLaunchScript();
    }
    if (testScript != null) {
      testScript = workspace.getRoot().getPath() + "/" + testScript;
    }
    return testScript;
  }

  /**
   * Creates settings for running tests with the given name within a Dart file.
   */
  @NotNull
  public static BazelTestFields forTestName(@NotNull String testName, @NotNull String path, @Nullable String additionalArgs) {
    return new BazelTestFields(testName, path, null, additionalArgs);
  }

  /**
   * Creates settings for running all the tests in a Dart file.
   */
  public static BazelTestFields forFile(@NotNull String path, @Nullable String additionalArgs) {
    return new BazelTestFields(null, path, null, additionalArgs);
  }

  /**
   * Creates settings for running all the tests in a Bazel target
   */
  public static BazelTestFields forTarget(@NotNull String target, @Nullable String additionalArgs) {
    return new BazelTestFields(null, null, target, additionalArgs);
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


  /**
   * Parameters to pass to the test runner, such as --watch.
   */
  @Nullable
  public String getAdditionalArgs() {
    return additionalArgs;
  }

  @NotNull
  BazelTestFields copy() {
    return new BazelTestFields(this);
  }

  @NotNull
  BazelTestFields copyTemplateToNonTemplate(@NotNull final Project project) {
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

    getScope(project).checkRunnable(this, project);
  }

  /**
   * Starts running the tests.
   */
  @NotNull
  ProcessHandler run(@NotNull final Project project, @NotNull final RunMode mode) throws ExecutionException {
    return new MostlySilentOsProcessHandler(getLaunchCommand(project, mode));
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

    final Workspace workspace = getWorkspace(project);

    final String launchingScript = getTestScriptFromWorkspace(project);
    assert launchingScript != null; // already checked

    final GeneralCommandLine commandLine = new GeneralCommandLine().withWorkDirectory(workspace.getRoot().getPath());
    commandLine.setCharset(CharsetToolkit.UTF8_CHARSET);
    commandLine.setExePath(FileUtil.toSystemDependentName(launchingScript));

    final String nonNullArgs = StringUtil.notNullize(additionalArgs);

    // User specified additional target arguments.
    final CommandLineTokenizer testArgsTokenizer = new CommandLineTokenizer(nonNullArgs);
    while (testArgsTokenizer.hasMoreTokens()) {
      commandLine.addParameter(testArgsTokenizer.nextToken());
    }
    commandLine.addParameter(Flags.noColor);
    // If the user did not turn the --machine flag off, we will pass it to the test runner.
    if (!nonNullArgs.contains(Flags.noMachine)) {
      commandLine.addParameter(Flags.machine);
    }
    final String relativeEntryFilePath = entryFile == null
                                         ? null
                                         : FileUtil.getRelativePath(workspace.getRoot().getPath(), entryFile, '/');
    switch (getScope(project)) {
      case NAME:
        commandLine.addParameters(Flags.name, testName);
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
      commandLine.addParameters(Flags.separator, Flags.enableDebugging);
    }
    return commandLine;
  }

  @Nullable
  protected Workspace getWorkspace(@NotNull Project project) {
    return WorkspaceCache.getInstance(project).get();
  }

  protected void verifyMainFile(Project project) throws RuntimeConfigurationError {
    final MainFile.Result main = MainFile.verify(entryFile, project);
    if (!main.canLaunch()) {
      throw new RuntimeConfigurationError(main.getError());
    }
  }

  /**
   * Checks if these fields provide a test-watching configuration (include the flag --watch).
   * <p>
   * See {@link BazelWatchTestConfigProducer} and {@link WatchFactory} for more information.
   */
  public boolean isWatchConfig() {
    return getAdditionalArgs() != null && getAdditionalArgs().contains(Flags.watch);
  }

  /**
   * Determines the type of test invocation we need to run: test-by-name, test-by-file, or test-by-bazel-target.
   *
   * <p>
   * We can assume the following about this BazelTestFields instance based on the Scope returned.
   *
   * <p>
   * <ul>
   * <li>Scope.NAME: The testName and entryFile fields are both non-null.  The bazelTarget field may be null.</li>
   * <li>Scope.FILE: The entryFile field is non-null.  The bazelTarget field may be null.</li>
   * <li>Scope.TARGET_PATTERN: The testName and entryFile fields may both be null.  If the bazelTarget field is non-null, this target is
   * runnable.</li>
   * </ul>
   */
  @NotNull
  public Scope getScope(@NotNull Project project) {
    if (testName != null && entryFile != null) {
      return Scope.NAME;
    }
    if (entryFile != null) {
      return Scope.FILE;
    }
    return Scope.TARGET_PATTERN;
  }

  public void writeTo(Element element) {
    ElementIO.addOption(element, "testName", testName);
    ElementIO.addOption(element, "entryFile", entryFile);
    ElementIO.addOption(element, "bazelTarget", bazelTarget);
    ElementIO.addOption(element, "additionalArgs", additionalArgs);
  }

  public static BazelTestFields readFrom(Element element) {
    final Map<String, String> options = ElementIO.readOptions(element);

    final String testName = options.get("testName");
    final String entryFile = options.get("entryFile");
    final String bazelTarget = options.get("bazelTarget");
    final String additionalArgs = options.get("additionalArgs");

    try {
      return new BazelTestFields(testName, entryFile, bazelTarget, additionalArgs);
    }
    catch (IllegalArgumentException e) {
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
        // The new bazel test runner could not be found.
        final Workspace workspace = fields.getWorkspace(project);
        if (workspace == null || workspace.getTestScript() == null) {
          throw new RuntimeConfigurationError(FlutterBundle.message("flutter.run.bazel.newBazelTestRunnerUnavailable"),
                                              () -> FlutterSettingsConfigurable.openFlutterSettings(project));
        }

        fields.verifyMainFile(project);
      }
    },

    TARGET_PATTERN("All tests in a bazel target or matching a bazel target pattern") {
      @Override
      public void checkRunnable(@NotNull BazelTestFields fields, @NotNull Project project) throws RuntimeConfigurationError {
        // check that bazel target is not empty
        if (StringUtil.isEmptyOrSpaces(fields.getBazelTarget())) {
          throw new RuntimeConfigurationError(FlutterBundle.message("flutter.run.bazel.noTargetSet"));
        }
        // check that the bazel target starts with "//"
        if (!fields.getBazelTarget().startsWith("//")) {
          throw new RuntimeConfigurationError(FlutterBundle.message("flutter.run.bazel.startWithSlashSlash"));
        }
      }
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

  /**
   * Flags and options that we pass on to the flutter runner or pay special attention to.
   */
  final static class Flags {
    /**
     * Flag passed to the test runner script.  Tells it to watch files, and re-run tests when the run completes.
     */
    static String watch = "--watch";

    /**
     * Flag passed to the test runner script.  Tells it to display results without any special text coloring.
     */
    static String noColor = "--no-color";

    /**
     * Option passed to the test runner script.  Tells it to only run tests matching a given name.
     */
    static String name = "--name";

    /**
     * Flag passed to the bazel test runner.  Tells it to allow the debugger to attach to the tests.
     */
    static String enableDebugging = "--enable-debugging";

    /**
     * Separator between groups of flags.  This distinguishes bazel args from additional args.
     */
    static String separator = "--";

    /**
     * Option passed to the bazel test runner.  Tells it to report test status in machine-readable JSON to show results in the UI.
     */
    static String machine = "--machine";

    /**
     * Option passed to the bazel test runner.  Tells it to report test status without machine-readable JSON and not show detailed results
     * in the UI.
     */
    static String noMachine = "--no-machine";

    // Don't allow construction of this class.
    private Flags() {
    }
  }
}
