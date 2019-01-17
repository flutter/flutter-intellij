/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.android;

import com.android.annotations.VisibleForTesting;
import com.android.prefs.AndroidLocation;
import com.android.tools.idea.gradle.project.common.GradleInitScripts;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.utils.SdkUtils;
import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.hash.HashMap;
import io.flutter.FlutterUtils;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.FN_GRADLE_WRAPPER_UNIX;
import static com.android.SdkConstants.FN_GRADLE_WRAPPER_WIN;

public class GradleDependencyFetcher {
  private static final Logger LOG = Logger.getInstance(GradleDependencyFetcher.class);
  private static final String DEP_COMMENT = " - ";
  private static final String DEP_LIST_ITEM = "+---";
  private static final String DEP_LIST_END = "\\---";
  private static final String DEP_LIST_TERMINATOR = "--- ";

  private final Project myProject;
  private final Map<String, List<String>> myDependencies;
  private String myType;
  private String mySection;
  private List<String> mySectionDependencies;

  public GradleDependencyFetcher(@NotNull Project project) {
    myProject = project;
    myDependencies = new HashMap<>(15);
  }

  @NotNull
  public Map<String, List<String>> getDependencies() {
    return myDependencies;
  }

  public void run() {
    String path = myProject.getBaseDir().getPath();
    File dir = new File(path, "android");
    runIn(dir);
  }

  private void runIn(@NotNull File base) {
    File gradlew = new File(base, isWindows() ? FN_GRADLE_WRAPPER_WIN : FN_GRADLE_WRAPPER_UNIX);
    if (!gradlew.exists() || !gradlew.canExecute()) {
      FlutterUtils.warn(LOG, "Cannot run gradle");
      return;
    }
    File pwd = base.getAbsoluteFile();
    List<String> args = Lists.newArrayList();
    args.add(gradlew.getAbsolutePath()); // TODO(messick): Determine if this needs to be run via a shell. Windows?
    String customizedGradleHome = System.getProperty("gradle.user.home");
    if (customizedGradleHome != null) {
      args.add("-g");
      args.add(customizedGradleHome);
    }
    args.add("androidDependencies");
    GradleInitScripts.getInstance().addLocalMavenRepoInitScriptCommandLineArg(args);
    GeneralCommandLine cmdLine = new GeneralCommandLine(args).withWorkDirectory(pwd);
    cmdLine.withEnvironment("JAVA_HOME", EmbeddedDistributionPaths.getInstance().getEmbeddedJdkPath().getAbsolutePath());
    try {
      cmdLine.withEnvironment("ANDROID_SDK_HOME", AndroidLocation.getFolder());
    }
    catch (AndroidLocation.AndroidLocationException e) {
      FlutterUtils.warn(LOG, "No Android SDK", e);
      return;
    }
    String result = "NO OUTPUT";
    try {
      CapturingProcessHandler process = new CapturingProcessHandler(cmdLine);
      int timeoutInMilliseconds = 2 * 60 * 1000;
      ProcessOutput processOutput = process.runProcess(timeoutInMilliseconds, true);
      if (processOutput.isTimeout()) {
        FlutterUtils.warn(LOG, "Dependency processing time-out");
        return;
      }
      String errors = processOutput.getStderr();
      String output = processOutput.getStdout();
      int exitCode = processOutput.getExitCode();
      if (exitCode != 0) {
        FlutterUtils.warn(LOG, "Error " + String.valueOf(exitCode) + " during dependency analysis: " + errors);
        return;
      }
      result = output;
    }
    catch (ExecutionException e) {
      FlutterUtils.warn(LOG, "Dependency process exception", e);
    }
    parseDependencies(result);
  }

  @VisibleForTesting
  public void parseDependencies(String result) {
    try (BufferedReader reader = new BufferedReader(new StringReader(result))) {
      reader.lines().forEach(this::parseLine);
    }
    catch (IOException ex) {
      FlutterUtils.warn(LOG, ex);
    }
  }

  private void parseLine(String line) {
    line = line.trim();
    if (mySection == null && !line.contains(DEP_COMMENT)) {
      myType = null;
    }
    if (myType == null) {
      myType = line;
      mySection = null;
      mySectionDependencies = null;
    }
    else if (mySection == null) {
      mySection = line.substring(0, line.indexOf(DEP_COMMENT));
      mySectionDependencies = Lists.newArrayList();
    }
    else if (line.startsWith(DEP_LIST_ITEM)) {
      mySectionDependencies.add(line.substring(line.indexOf(DEP_LIST_TERMINATOR) + DEP_LIST_TERMINATOR.length()));
    }
    else if (line.startsWith(DEP_LIST_END)) {
      mySectionDependencies.add(line.substring(line.indexOf(DEP_LIST_TERMINATOR) + DEP_LIST_TERMINATOR.length()));
      myDependencies.put(mySection, mySectionDependencies);
      mySection = null;
    }
  }

  private static boolean isWindows() {
    return SdkUtils.startsWithIgnoreCase(System.getProperty("os.name"), "windows");
  }
}
