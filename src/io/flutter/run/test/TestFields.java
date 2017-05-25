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
import io.flutter.pub.PubRoot;
import io.flutter.run.MainFile;
import io.flutter.run.daemon.FlutterDevice;
import io.flutter.run.daemon.RunMode;
import io.flutter.sdk.FlutterSdk;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Settings for running a Flutter test.
 */
public class TestFields {
  @NotNull
  private final String testFile;

  TestFields(@NotNull String testFile) {
    this.testFile = testFile;
  }

  /**
   * The Dart file containing the tests to run.
   */
  @NotNull
  public String getTestFile() {
    return testFile;
  }

  void writeTo(Element elt) {
    addOption(elt, "testFile", testFile);
  }

  /**
   * Reads the fields from an XML Element, if available.
   */
  @NotNull
  static TestFields readFrom(Element elt) throws InvalidDataException {
    final Map<String, String> options = readOptions(elt);

    final String testFile = options.get("testFile");
    if (testFile == null) {
      throw new InvalidDataException("can't read testFile option in run configuration");
    }

    return new TestFields(testFile);
  }

  /**
   * Reports any errors that the user should correct.
   * <p>
   * This will be called while the user is typing; see RunConfiguration.checkConfiguration.
   */
  void checkRunnable(@NotNull Project project) throws RuntimeConfigurationError {
    checkSdk(project);

    final MainFile.Result main = MainFile.verify(testFile, project);
    if (!main.canLaunch()) {
      throw new RuntimeConfigurationError(main.getError());
    }

    final PubRoot root = PubRoot.forDirectory(main.get().getAppDir());
    if (root == null) {
      throw new RuntimeConfigurationError("Test file isn't within a Flutter pub root");
    }
  }

  /**
   * Starts running the tests.
   */
  ProcessHandler run(Project project, FlutterDevice device, RunMode mode) throws ExecutionException {
    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null) {
      throw new ExecutionException("The Flutter SDK is not configured");
    }

    final MainFile main = MainFile.verify(testFile, project).get();
    final PubRoot root = PubRoot.forDirectory(main.getAppDir());
    if (root == null) {
      throw new ExecutionException("Test file isn't within a Flutter pub root");
    }

    return sdk.flutterTest(root, main.getFile(), device).startProcess(project);
  }

  private void checkSdk(@NotNull Project project) throws RuntimeConfigurationError {
    if (FlutterSdk.getFlutterSdk(project) == null) {
      throw new RuntimeConfigurationError("Flutter SDK isn't set");
    }
  }

  private void addOption(Element elt, String name, String value) {
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
}
