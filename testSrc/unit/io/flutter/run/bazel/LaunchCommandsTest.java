/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazel;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.mock.MockVirtualFileSystem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.run.daemon.FlutterDevice;
import io.flutter.run.daemon.RunMode;
import io.flutter.testing.FlutterModuleFixture;
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.Testing;
import org.fest.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class LaunchCommandsTest {
  @Rule
  public ProjectFixture projectFixture = Testing.makeCodeInsightModule();

  @Rule
  public FlutterModuleFixture flutterFixture = new FlutterModuleFixture(projectFixture, false);

  @Test
  public void shouldProduceCorrectCommandLineInReleaseMode() {
    final BazelFields fields = new FakeBazelTestFields();
    fields.setEntryFile("/tmp/foo/lib/main.dart");
    fields.setLaunchingScript("bazel-run.sh");
    fields.setBazelTarget("bazel_target");
    fields.setEnableReleaseMode(true);
    fields.setAdditionalArgs("additional_args");

    final FlutterDevice device = FlutterDevice.getTester();
    GeneralCommandLine launchCommand = null;
    try {
      launchCommand = fields.getLaunchCommand(flutterFixture.getModule().getProject(), device, RunMode.RUN);
    } catch (ExecutionException e) {
      e.printStackTrace();
    }
    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("bazel-run.sh");
    expectedCommandLine.add("--define");
    expectedCommandLine.add("flutter_build_mode=release");
    expectedCommandLine.add("bazel_target");
    expectedCommandLine.add("--");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("additional_args");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("flutter-tester");
    assertThat(launchCommand.getCommandLineString(), equalTo(String.join(" ", expectedCommandLine)));

    // When release mode is enabled, using different RunModes has no effect.

    launchCommand = null;
    try {
      launchCommand = fields.getLaunchCommand(flutterFixture.getModule().getProject(), device, RunMode.DEBUG);
    } catch (ExecutionException e) {
      e.printStackTrace();
    }
    assertThat(launchCommand.getCommandLineString(), equalTo(String.join(" ", expectedCommandLine)));

    launchCommand = null;
    try {
      launchCommand = fields.getLaunchCommand(flutterFixture.getModule().getProject(), device, RunMode.PROFILE);
    } catch (ExecutionException e) {
      e.printStackTrace();
    }
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void shouldProduceCorrectCommandLineInRunMode() {
    final BazelFields fields = new FakeBazelTestFields();
    fields.setEntryFile("/tmp/foo/lib/main.dart");
    fields.setLaunchingScript("bazel-run.sh");
    fields.setBazelTarget("bazel_target");
    fields.setEnableReleaseMode(false);
    fields.setAdditionalArgs("additional_args");

    final FlutterDevice device = FlutterDevice.getTester();
    GeneralCommandLine launchCommand = null;
    try {
      launchCommand = fields.getLaunchCommand(flutterFixture.getModule().getProject(), device, RunMode.RUN);
    } catch (ExecutionException e) {
      e.printStackTrace();
    }
    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("bazel-run.sh");
    expectedCommandLine.add("--define");
    expectedCommandLine.add("flutter_build_mode=debug");
    expectedCommandLine.add("bazel_target");
    expectedCommandLine.add("--");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("additional_args");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("flutter-tester");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void shouldProduceCorrectCommandLineInDebugMode() {
    final BazelFields fields = new FakeBazelTestFields();
    fields.setEntryFile("/tmp/foo/lib/main.dart");
    fields.setLaunchingScript("bazel-run.sh");
    fields.setBazelTarget("bazel_target");
    fields.setEnableReleaseMode(false);
    fields.setAdditionalArgs("additional_args");

    final FlutterDevice device = FlutterDevice.getTester();
    GeneralCommandLine launchCommand = null;
    try {
      launchCommand = fields.getLaunchCommand(flutterFixture.getModule().getProject(), device, RunMode.DEBUG);
    } catch (ExecutionException e) {
      e.printStackTrace();
    }
    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("bazel-run.sh");
    expectedCommandLine.add("--define");
    expectedCommandLine.add("flutter_build_mode=debug");
    expectedCommandLine.add("bazel_target");
    expectedCommandLine.add("--");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("additional_args");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("flutter-tester");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void shouldProduceCorrectCommandLineInProfileMode() {
    final BazelFields fields = new FakeBazelTestFields();
    fields.setEntryFile("/tmp/foo/lib/main.dart");
    fields.setLaunchingScript("bazel-run.sh");
    fields.setBazelTarget("bazel_target");
    fields.setEnableReleaseMode(false);
    fields.setAdditionalArgs("additional_args");

    final FlutterDevice device = FlutterDevice.getTester();
    GeneralCommandLine launchCommand = null;
    try {
      launchCommand = fields.getLaunchCommand(flutterFixture.getModule().getProject(), device, RunMode.PROFILE);
    } catch (ExecutionException e) {
      e.printStackTrace();
    }
    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("bazel-run.sh");
    expectedCommandLine.add("--define");
    expectedCommandLine.add("flutter_build_mode=profile");
    expectedCommandLine.add("bazel_target");
    expectedCommandLine.add("--");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("additional_args");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("flutter-tester");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  private static class FakeBazelTestFields extends BazelFields {
    MockVirtualFileSystem fs = new MockVirtualFileSystem();

    @Override
    void checkRunnable(@NotNull Project project) {}

    @Override
    @Nullable
    protected VirtualFile getAppDir(@NotNull Project project) {
      if (getEntryFile() == null)
        return null;
      @NotNull
      final String entryFile = getEntryFile();
      return fs.refreshAndFindFileByPath(entryFile.replace("main.dart", ""));
    }
  }
}
