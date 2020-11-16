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
import com.intellij.openapi.util.Pair;
import io.flutter.bazel.FakeWorkspaceFactory;
import io.flutter.bazel.Workspace;
import io.flutter.run.FlutterDevice;
import io.flutter.run.common.RunMode;
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
  public void producesCorrectCommandLineInReleaseMode() throws ExecutionException {
    final BazelFields fields = setupBazelFields(
      "bazel_target",
      null,
      null,
      true
    );

    final FlutterDevice device = FlutterDevice.getTester();
    GeneralCommandLine launchCommand = fields.getLaunchCommand(projectFixture.getProject(), device, RunMode.RUN);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("/workspace/scripts/flutter-run.sh");
    expectedCommandLine.add("--bazel-options=--define=flutter_build_mode=release");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("flutter-tester");
    expectedCommandLine.add("bazel_target");
    assertThat(launchCommand.getCommandLineString(), equalTo(String.join(" ", expectedCommandLine)));

    // When release mode is enabled, using different RunModes has no effect.

    launchCommand = fields.getLaunchCommand(projectFixture.getProject(), device, RunMode.DEBUG);
    assertThat(launchCommand.getCommandLineString(), equalTo(String.join(" ", expectedCommandLine)));

    launchCommand = fields.getLaunchCommand(projectFixture.getProject(), device, RunMode.PROFILE);
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void producesCorrectCommandLineInRunMode() throws ExecutionException {
    final BazelFields fields = setupBazelFields();

    final FlutterDevice device = FlutterDevice.getTester();
    final GeneralCommandLine launchCommand = fields.getLaunchCommand(projectFixture.getProject(), device, RunMode.RUN);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("/workspace/scripts/flutter-run.sh");
    expectedCommandLine.add("--bazel-options=--define=flutter_build_mode=debug");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("flutter-tester");
    expectedCommandLine.add("bazel_target");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void producesCorrectCommandLineWithBazelArgs() throws ExecutionException {
    final BazelFields fields = setupBazelFields(
      "bazel_target",
      "--define=bazel_args",
      null,
      false
    );

    final FlutterDevice device = FlutterDevice.getTester();
    final GeneralCommandLine launchCommand = fields.getLaunchCommand(projectFixture.getProject(), device, RunMode.RUN);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("/workspace/scripts/flutter-run.sh");
    expectedCommandLine.add("--bazel-options=--define=bazel_args --define=flutter_build_mode=debug");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("flutter-tester");
    expectedCommandLine.add("bazel_target");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void overridesRunModeFromBazelArgs() throws ExecutionException {
    BazelFields fields = setupBazelFields(
      "bazel_target",
      "--define=flutter_build_mode=release",
      null,
      false
    );

    final FlutterDevice device = FlutterDevice.getTester();
    GeneralCommandLine launchCommand = fields.getLaunchCommand(projectFixture.getProject(), device, RunMode.RUN);

    List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("/workspace/scripts/flutter-run.sh");
    expectedCommandLine.add("--bazel-options=--define=flutter_build_mode=release");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("flutter-tester");
    expectedCommandLine.add("bazel_target");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));

    // With a space instead of an =
    fields = setupBazelFields(
      "bazel_target",
      "--define flutter_build_mode=profile",
      null,
      false
    );

    launchCommand = fields.getLaunchCommand(projectFixture.getProject(), device, RunMode.RUN);

    expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("/workspace/scripts/flutter-run.sh");
    expectedCommandLine.add("--bazel-options=--define flutter_build_mode=profile");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("flutter-tester");
    expectedCommandLine.add("bazel_target");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));

    // With multiple params
    fields = setupBazelFields(
      "bazel_target",
      "--define param1=2 --define=param2=2 --define=flutter_build_mode=profile",
      null,
      false
    );

    launchCommand = fields.getLaunchCommand(projectFixture.getProject(), device, RunMode.RUN);

    expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("/workspace/scripts/flutter-run.sh");
    expectedCommandLine.add("--bazel-options=--define param1=2 --define=param2=2 --define=flutter_build_mode=profile");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("flutter-tester");
    expectedCommandLine.add("bazel_target");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void producesCorrectCommandLineWithAdditionalArgs() throws ExecutionException {
    final BazelFields fields = setupBazelFields(
      "bazel_target",
      null,
      "additional_args",
      false
    );

    final FlutterDevice device = FlutterDevice.getTester();
    final GeneralCommandLine launchCommand = fields.getLaunchCommand(projectFixture.getProject(), device, RunMode.RUN);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("/workspace/scripts/flutter-run.sh");
    expectedCommandLine.add("--bazel-options=--define=flutter_build_mode=debug");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("additional_args");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("flutter-tester");
    expectedCommandLine.add("bazel_target");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void producesCorrectCommandLineWithBazelAndAdditionalArgs() throws ExecutionException {
    final BazelFields fields = setupBazelFields(
      "bazel_target",
      "--define=bazel_args0 --define bazel_args1=value",
      "--additional_args1 --additional_args2 value_of_arg2",
      false
    );

    final FlutterDevice device = FlutterDevice.getTester();
    final GeneralCommandLine launchCommand = fields.getLaunchCommand(projectFixture.getProject(), device, RunMode.RUN);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("/workspace/scripts/flutter-run.sh");
    expectedCommandLine.add("--bazel-options=--define=bazel_args0 --define bazel_args1=value --define=flutter_build_mode=debug");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("--additional_args1");
    expectedCommandLine.add("--additional_args2");
    expectedCommandLine.add("value_of_arg2");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("flutter-tester");
    expectedCommandLine.add("bazel_target");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void producesCorrectCommandLineInDebugMode() throws ExecutionException {
    final BazelFields fields = setupBazelFields();

    final FlutterDevice device = FlutterDevice.getTester();
    final GeneralCommandLine launchCommand =
      fields.getLaunchCommand(projectFixture.getProject(), device, RunMode.DEBUG);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("/workspace/scripts/flutter-run.sh");
    expectedCommandLine.add("--bazel-options=--define=flutter_build_mode=debug");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("--start-paused");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("flutter-tester");
    expectedCommandLine.add("bazel_target");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void producesCorrectCommandLineInProfileMode() throws ExecutionException {
    final BazelFields fields = setupBazelFields();

    final FlutterDevice device = FlutterDevice.getTester();
    final GeneralCommandLine launchCommand =
      fields.getLaunchCommand(projectFixture.getProject(), device, RunMode.PROFILE);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("/workspace/scripts/flutter-run.sh");
    expectedCommandLine.add("--bazel-options=--define=flutter_build_mode=profile");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("flutter-tester");
    expectedCommandLine.add("bazel_target");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void producesCorrectCommandLineWithAndroidDevice() throws ExecutionException {
    final BazelFields fields = setupBazelFields();

    final FlutterDevice device = new FlutterDevice("android-tester", "android device", "android", false);
    final GeneralCommandLine launchCommand = fields.getLaunchCommand(projectFixture.getProject(), device, RunMode.RUN);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("/workspace/scripts/flutter-run.sh");
    expectedCommandLine.add("--bazel-options=--define=flutter_build_mode=debug");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("android-tester");
    expectedCommandLine.add("bazel_target");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }


  @Test
  public void producesCorrectCommandLineWithAndroidEmulator() throws ExecutionException {
    final BazelFields fields = setupBazelFields();

    final FlutterDevice device = new FlutterDevice("android-tester", "android device", "android", true);
    final GeneralCommandLine launchCommand = fields.getLaunchCommand(projectFixture.getProject(), device, RunMode.RUN);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("/workspace/scripts/flutter-run.sh");
    expectedCommandLine.add("--bazel-options=--define=flutter_build_mode=debug");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("android-tester");
    expectedCommandLine.add("bazel_target");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void producesCorrectCommandLineWithIosDevice() throws ExecutionException {
    final BazelFields fields = setupBazelFields();

    final FlutterDevice device = new FlutterDevice("ios-tester", "ios device", "ios", false);
    final GeneralCommandLine launchCommand = fields.getLaunchCommand(projectFixture.getProject(), device, RunMode.RUN);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("/workspace/scripts/flutter-run.sh");
    expectedCommandLine.add("--bazel-options=--define=flutter_build_mode=debug");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("ios-tester");
    expectedCommandLine.add("bazel_target");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void producesCorrectCommandLineWithIosSimulator() throws ExecutionException {
    final BazelFields fields = setupBazelFields();

    final FlutterDevice device = new FlutterDevice("ios-tester", "ios device", "ios", true);
    final GeneralCommandLine launchCommand = fields.getLaunchCommand(projectFixture.getProject(), device, RunMode.RUN);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("/workspace/scripts/flutter-run.sh");
    expectedCommandLine.add("--bazel-options=--define=flutter_build_mode=debug");
    expectedCommandLine.add("--machine");
    expectedCommandLine.add("-d");
    expectedCommandLine.add("ios-tester");
    expectedCommandLine.add("bazel_target");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }


  /**
   * Default configuration for the bazel test fields.
   */
  private BazelFields setupBazelFields(
    @Nullable String bazelTarget,
    @Nullable String bazelArgs,
    @Nullable String additionalArgs,
    boolean enableReleaseMode) {
    return new FakeBazelFields(new BazelFields(
      bazelTarget,
      bazelArgs,
      additionalArgs,
      enableReleaseMode
    ));
  }

  private BazelFields setupBazelFields() {
    return setupBazelFields("bazel_target", null, null, false);
  }

  /**
   * Fake bazel fields that doesn't depend on the Dart SDK.
   */
  private static class FakeBazelFields extends BazelFields {
    MockVirtualFileSystem fs;
    final Workspace fakeWorkspace;

    FakeBazelFields(@NotNull BazelFields template,
                    @Nullable String daemonScript,
                    @Nullable String doctorScript,
                    @Nullable String launchScript,
                    @Nullable String testScript,
                    @Nullable String runScript,
                    @Nullable String sdkHome,
                    @Nullable String versionFile) {
      super(template);
      final Pair.NonNull<MockVirtualFileSystem, Workspace> pair = FakeWorkspaceFactory
        .createWorkspaceAndFilesystem(daemonScript, doctorScript, launchScript, testScript, runScript, sdkHome, versionFile, null);
      fs = pair.first;
      fakeWorkspace = pair.second;
    }

    FakeBazelFields(@NotNull BazelFields template) {
      super(template);
      final Pair.NonNull<MockVirtualFileSystem, Workspace> pair = FakeWorkspaceFactory
        .createWorkspaceAndFilesystem();
      fs = pair.first;
      fakeWorkspace = pair.second;
    }


    @Override
    void checkRunnable(@NotNull Project project) {
    }

    @Nullable
    @Override
    protected Workspace getWorkspace(@NotNull Project project) {
      return fakeWorkspace;
    }
  }
}
