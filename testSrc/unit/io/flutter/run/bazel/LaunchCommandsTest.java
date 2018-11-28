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
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.Testing;
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

  @Test
  public void producesCorrectCommandLineInReleaseMode() {
    final BazelFields fields = new FakeBazelTestFields();
    setupBazelFields(fields);
    fields.setEnableReleaseMode(true);

    final FlutterDevice device = FlutterDevice.getTester();
    GeneralCommandLine launchCommand = getLaunchCommand(fields, device, projectFixture, RunMode.RUN);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("bazel-run.sh");
    expectedCommandLine.add("--define");
    expectedCommandLine.add("flutter_build_mode=release");
    expectedCommandLine.add("bazel_target");
    expectedCommandLine.add("--");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("flutter-tester");
    assertThat(launchCommand.getCommandLineString(), equalTo(String.join(" ", expectedCommandLine)));

    // When release mode is enabled, using different RunModes has no effect.

    launchCommand = null;
    try {
      launchCommand = fields.getLaunchCommand(projectFixture.getProject(), device, RunMode.DEBUG);
    } catch (ExecutionException e) {
      e.printStackTrace();
    }
    assertThat(launchCommand.getCommandLineString(), equalTo(String.join(" ", expectedCommandLine)));

    launchCommand = null;
    try {
      launchCommand = fields.getLaunchCommand(projectFixture.getProject(), device, RunMode.PROFILE);
    } catch (ExecutionException e) {
      e.printStackTrace();
    }
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void producesCorrectCommandLineInRunMode() {
    final BazelFields fields = new FakeBazelTestFields();
    setupBazelFields(fields);

    final FlutterDevice device = FlutterDevice.getTester();
    GeneralCommandLine launchCommand = getLaunchCommand(fields, device, projectFixture, RunMode.RUN);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("bazel-run.sh");
    expectedCommandLine.add("--define");
    expectedCommandLine.add("flutter_build_mode=debug");
    expectedCommandLine.add("bazel_target");
    expectedCommandLine.add("--");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("flutter-tester");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void producesCorrectCommandLineWithAdditionalArgs() {
    final BazelFields fields = new FakeBazelTestFields();
    setupBazelFields(fields);
    fields.setAdditionalArgs("additional_args");

    final FlutterDevice device = FlutterDevice.getTester();
    GeneralCommandLine launchCommand = getLaunchCommand(fields, device, projectFixture, RunMode.RUN);

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
  public void producesCorrectCommandLineInDebugMode() {
    final BazelFields fields = new FakeBazelTestFields();
    setupBazelFields(fields);

    final FlutterDevice device = FlutterDevice.getTester();
    GeneralCommandLine launchCommand =
      getLaunchCommand(fields, device, projectFixture, RunMode.DEBUG);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("bazel-run.sh");
    expectedCommandLine.add("--define");
    expectedCommandLine.add("flutter_build_mode=debug");
    expectedCommandLine.add("bazel_target");
    expectedCommandLine.add("--");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("--start-paused");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("flutter-tester");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void producesCorrectCommandLineInProfileMode() {
    final BazelFields fields = new FakeBazelTestFields();
    setupBazelFields(fields);

    final FlutterDevice device = FlutterDevice.getTester();
    GeneralCommandLine launchCommand =
      getLaunchCommand(fields, device, projectFixture, RunMode.PROFILE);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("bazel-run.sh");
    expectedCommandLine.add("--define");
    expectedCommandLine.add("flutter_build_mode=profile");
    expectedCommandLine.add("bazel_target");
    expectedCommandLine.add("--");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("flutter-tester");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void producesCorrectCommandLineWithAndroidDevice() {
    final BazelFields fields = new FakeBazelTestFields();
    setupBazelFields(fields);

    final FlutterDevice device = new FlutterDevice("android-tester", "android device", "android", false);
    GeneralCommandLine launchCommand = getLaunchCommand(fields, device, projectFixture, RunMode.RUN);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("bazel-run.sh");
    expectedCommandLine.add("--define");
    expectedCommandLine.add("flutter_build_mode=debug");
    expectedCommandLine.add("bazel_target");
    expectedCommandLine.add("--");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("android-tester");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }


  @Test
  public void producesCorrectCommandLineWithAndroidEmulator() {
    final BazelFields fields = new FakeBazelTestFields();
    setupBazelFields(fields);

    final FlutterDevice device = new FlutterDevice("android-tester", "android device", "android", true);
    GeneralCommandLine launchCommand = getLaunchCommand(fields, device, projectFixture, RunMode.RUN);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("bazel-run.sh");
    expectedCommandLine.add("--define");
    expectedCommandLine.add("flutter_build_mode=debug");
    expectedCommandLine.add("bazel_target");
    expectedCommandLine.add("--");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("android-tester");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void producesCorrectCommandLineWithIosDevice() {
    final BazelFields fields = new FakeBazelTestFields();
    setupBazelFields(fields);

    final FlutterDevice device = new FlutterDevice("ios-tester", "ios device", "ios", false);
    GeneralCommandLine launchCommand = getLaunchCommand(fields, device, projectFixture, RunMode.RUN);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("bazel-run.sh");
    expectedCommandLine.add("--define");
    expectedCommandLine.add("flutter_build_mode=debug");
    expectedCommandLine.add("--ios_multi_cpus=arm64");
    expectedCommandLine.add("bazel_target");
    expectedCommandLine.add("--");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("ios-tester");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void producesCorrectCommandLineWithIosSimulator() {
    final BazelFields fields = new FakeBazelTestFields();
    setupBazelFields(fields);

    final FlutterDevice device = new FlutterDevice("ios-tester", "ios device", "ios", true);
    GeneralCommandLine launchCommand = getLaunchCommand(fields, device, projectFixture, RunMode.RUN);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("bazel-run.sh");
    expectedCommandLine.add("--define");
    expectedCommandLine.add("flutter_build_mode=debug");
    expectedCommandLine.add("--ios_multi_cpus=x86_64");
    expectedCommandLine.add("bazel_target");
    expectedCommandLine.add("--");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("ios-tester");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }


  /**
   * Gets the command line to run a test app.
   */
  @Nullable
  private static GeneralCommandLine getLaunchCommand(BazelFields fields,
                                                     FlutterDevice device,
                                                     ProjectFixture projectFixture,
                                                     RunMode run) {
    GeneralCommandLine launchCommand = null;
    try {
      launchCommand = fields.getLaunchCommand(projectFixture.getProject(), device, run);
    }
    catch (ExecutionException e) {
      e.printStackTrace();
    }
    return launchCommand;
  }


  /**
   * Default configuration for the bazel test fields.
   */
  private void setupBazelFields(BazelFields fields) {
    fields.setEntryFile("/tmp/foo/lib/main.dart");
    fields.setLaunchingScript("bazel-run.sh");
    fields.setBazelTarget("bazel_target");
    fields.setEnableReleaseMode(false);
  }

  /**
   * Fake bazel test fields that doesn't depend on the Dart SDK.
   */
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
      
      // With the default entryFile from setupBazelFields, the application directory is the container of lib/main.dart.
      return fs.refreshAndFindFileByPath(entryFile.replace("lib/main.dart", ""));
    }
  }
}
