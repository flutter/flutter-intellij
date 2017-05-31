/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.test;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.pub.PubRoot;
import io.flutter.run.MainFile;
import io.flutter.run.daemon.RunMode;
import io.flutter.sdk.FlutterSdk;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Settings for running a Flutter test.
 */
public class TestFields {
  @Nullable
  private final String testFile;

  @Nullable
  private final String testDir;

  private TestFields(@Nullable String testFile, @Nullable String testDir) {
    if (testFile == null && testDir == null) {
      throw new IllegalArgumentException("either testFile or testDir must be non-null");
    } else if (testFile != null && testDir != null) {
      throw new IllegalArgumentException("either testFile or testDir must be null");
    }
    this.testFile = testFile;
    this.testDir = testDir;
  }

  /**
   * Creates settings for running all the tests in a Dart file.
   */
  public static TestFields forFile(String path) {
    return new TestFields(path, null);
  }

  /**
   * Creates settings for running all the tests in directory.
   */
  public static TestFields forDir(String path) {
    return new TestFields(null, path);
  }

  /**
   * Returns a value indicating whether we're running tests in a file or in a directory.
   */
  @NotNull
  public Scope getScope() {
    return testFile != null ? Scope.FILE : Scope.DIRECTORY;
  }

  /**
   * The Dart file containing the tests to run, or null if we are running tests in a directory.
   */
  @Nullable
  public String getTestFile() {
    return testFile;
  }

  /**
   * The directory containing the tests to run, or null if we are running tests in a file.
   */
  @Nullable
  public String getTestDir() {
    return testDir;
  }

  /**
   * Returns the file or directory containing the tests to run, or null if it doesn't exist.
   */
  @Nullable
  public VirtualFile getFileOrDir() {
    final String path = testFile != null ? testFile : testDir;
    if (path == null) return null;
    return LocalFileSystem.getInstance().findFileByPath(path);
  }

  /**
   * Returns the PubRoot containing the file or directory being tested, or null if none.
   */
  @Nullable
  public PubRoot getPubRoot(@NotNull Project project) {
    final VirtualFile dir = getFileOrDir();
    return (dir == null) ? null : PubRoot.forDescendant(dir, project);
  }

  /**
   * Returns the relative path to the file or directory from the pub root, or null if not in a pub root.
   */
  @Nullable
  public String getRelativePath(@NotNull Project project) {
    final PubRoot root = getPubRoot(project);
    if (root == null) return null;

    final VirtualFile fileOrDir = getFileOrDir();
    if (fileOrDir == null) return null;

    return root.getRelativePath(fileOrDir);
  }

  /**
   * Generates a name for these test settings, if they are valid.
   */
  @NotNull
  public String getSuggestedName(@NotNull Project project, @NotNull String defaultName) {

    switch (getScope()) {
      case FILE:
        final VirtualFile file = getFileOrDir();
        if (file == null) return defaultName;
        return "tests in " + file.getName();
      case DIRECTORY:
        final String relativePath = getRelativePath(project);
        if (relativePath != null) return "tests in " + relativePath;

        // check if it's the pub root itself.
        final PubRoot root = getPubRoot(project);
        if (root != null && root.getRoot().equals(getFileOrDir())) {
          return "All tests in " + root.getRoot().getName();
        }
    }
    return defaultName;
  }

  void writeTo(Element elt) {
    addOption(elt, "testFile", testFile);
    addOption(elt, "testDir", testDir);
  }

  /**
   * Reads the fields from an XML Element, if available.
   */
  @NotNull
  static TestFields readFrom(Element elt) throws InvalidDataException {
    final Map<String, String> options = readOptions(elt);

    final String testFile = options.get("testFile");
    final String testDir = options.get("testDir");
    try {
      return new TestFields(testFile, testDir);
    } catch (IllegalArgumentException e) {
      throw new InvalidDataException(e.getMessage());
    }
  }

  /**
   * Reports any errors that the user should correct.
   * <p>
   * This will be called while the user is typing; see RunConfiguration.checkConfiguration.
   */
  void checkRunnable(@NotNull Project project) throws RuntimeConfigurationError {
    checkSdk(project);
    getScope().checkRunnable(this, project);
  }

  /**
   * Starts running the tests.
   */
  ProcessHandler run(Project project, RunMode mode) throws ExecutionException {
    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null) {
      throw new ExecutionException("The Flutter SDK is not configured");
    }

    final VirtualFile fileOrDir = getFileOrDir();
    if (fileOrDir == null) {
      throw new ExecutionException("File or directory not found");
    }

    final PubRoot root = getPubRoot(project);
    if (root == null) {
      throw new ExecutionException("Test file isn't within a Flutter pub root");
    }

    return sdk.flutterTest(root, fileOrDir).startProcess(project);
  }

  private void checkSdk(@NotNull Project project) throws RuntimeConfigurationError {
    if (FlutterSdk.getFlutterSdk(project) == null) {
      throw new RuntimeConfigurationError("Flutter SDK isn't set");
    }
  }

  private void addOption(@NotNull Element elt, @NotNull String name, @Nullable String value) {
    if (value == null) return;

    final Element child = new Element("option");
    child.setAttribute("name", name);
    child.setAttribute("value", value);
    elt.addContent(child);
  }

  private static Map<String, String> readOptions(Element elt) {
    final Map<String, String> result = new HashMap<>();
    for (Element child : elt.getChildren()) {
      if ("option".equals(child.getName())) {
        final String name = child.getAttributeValue("name");
        final String value = child.getAttributeValue("value");
        if (name != null && value != null) {
          result.put(name, value);
        }
      }
    }
    return result;
  }

  /**
   * Selects which tests to run.
   */
  public enum Scope {
    FILE("All in file") {
      @Override
      public void checkRunnable(@NotNull TestFields fields, @NotNull Project project) throws RuntimeConfigurationError {
        final MainFile.Result main = MainFile.verify(fields.testFile, project);
        if (!main.canLaunch()) {
          throw new RuntimeConfigurationError(main.getError());
        }
        final PubRoot root = PubRoot.forDirectory(main.get().getAppDir());
        if (root == null) {
          throw new RuntimeConfigurationError("Test file isn't within a Flutter pub root");
        }
      }
    },

    DIRECTORY("All in directory") {
      @Override
      public void checkRunnable(@NotNull TestFields fields, @NotNull Project project) throws RuntimeConfigurationError {
        final VirtualFile dir = fields.getFileOrDir();
        if (dir == null) {
          throw new RuntimeConfigurationError("Directory not found");
        }
        final PubRoot root = PubRoot.forDescendant(dir, project);
        if (root == null) {
          throw new RuntimeConfigurationError("Directory is not in a pub root");
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

    public abstract void checkRunnable(@NotNull TestFields fields, @NotNull Project project) throws RuntimeConfigurationError;
  }
}
