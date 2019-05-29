/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.mock.MockVirtualFileSystem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import io.flutter.bazel.FakeWorkspaceFactory;
import io.flutter.bazel.Workspace;
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
import static org.junit.Assert.assertTrue;

public class LaunchCommandsTest {
  @Rule
  public ProjectFixture projectFixture = Testing.makeCodeInsightModule();

  @Test
  public void producesCorrectCommandLineForBazelTarget() throws ExecutionException {
    final BazelTestFields fields = forTarget("//foo:test");
    final GeneralCommandLine launchCommand = fields.getLaunchCommand(projectFixture.getProject(), RunMode.RUN);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("/workspace/scripts/flutter-test.sh");
    expectedCommandLine.add("--no-color");
    expectedCommandLine.add("//foo:test");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void producesCorrectCommandLineForBazelTargetInDebugMode() throws ExecutionException {
    final BazelTestFields fields = forTarget("//foo:test");
    final GeneralCommandLine launchCommand = fields.getLaunchCommand(projectFixture.getProject(), RunMode.DEBUG);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("/workspace/scripts/flutter-test.sh");
    expectedCommandLine.add("--no-color");
    expectedCommandLine.add("//foo:test");
    expectedCommandLine.add("--");
    expectedCommandLine.add("--enable-debugging");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }


  @Test
  public void producesCorrectCommandLineForFile() throws ExecutionException {
    final BazelTestFields fields = forFile("/workspace/foo/test/foo_test.dart");
    final GeneralCommandLine launchCommand = fields.getLaunchCommand(projectFixture.getProject(), RunMode.RUN);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("/workspace/scripts/flutter-test.sh");
    expectedCommandLine.add("--no-color");
    expectedCommandLine.add("foo/test/foo_test.dart");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void producesCorrectCommandLineForFileInDebugMode() throws ExecutionException {
    final BazelTestFields fields = forFile("/workspace/foo/test/foo_test.dart");
    final GeneralCommandLine launchCommand = fields.getLaunchCommand(projectFixture.getProject(), RunMode.DEBUG);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("/workspace/scripts/flutter-test.sh");
    expectedCommandLine.add("--no-color");
    expectedCommandLine.add("foo/test/foo_test.dart");
    expectedCommandLine.add("--");
    expectedCommandLine.add("--enable-debugging");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void producesCorrectCommandLineForTestName() throws ExecutionException {
    final BazelTestFields fields = forTestName("first test", "/workspace/foo/test/foo_test.dart");
    final GeneralCommandLine launchCommand = fields.getLaunchCommand(projectFixture.getProject(), RunMode.RUN);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("/workspace/scripts/flutter-test.sh");
    expectedCommandLine.add("--no-color");
    expectedCommandLine.add("--name");
    expectedCommandLine.add("first test");
    expectedCommandLine.add("foo/test/foo_test.dart");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void producesCorrectCommandLineForTestNameInDebugMode() throws ExecutionException {
    final BazelTestFields fields = forTestName("first test", "/workspace/foo/test/foo_test.dart");
    final GeneralCommandLine launchCommand = fields.getLaunchCommand(projectFixture.getProject(), RunMode.DEBUG);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("/workspace/scripts/flutter-test.sh");
    expectedCommandLine.add("--no-color");
    expectedCommandLine.add("--name");
    expectedCommandLine.add("first test");
    expectedCommandLine.add("foo/test/foo_test.dart");
    expectedCommandLine.add("--");
    expectedCommandLine.add("--enable-debugging");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }


  @Test
  public void producesCorrectCommandLineForBazelTargetWithoutTestScript() throws ExecutionException {
    final BazelTestFields fields = new FakeBazelTestFields(
      BazelTestFields.forTarget("//foo:test", null),
      "scripts/daemon.sh",
      "scripts/doctor.sh",
      "scripts/launch.sh",
      null,
      null,
      null
    );
    final GeneralCommandLine launchCommand = fields.getLaunchCommand(projectFixture.getProject(), RunMode.RUN);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("/workspace/scripts/launch.sh");
    expectedCommandLine.add("//foo:test");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void producesCorrectCommandLineForBazelTargetWithoutTestScriptInDebugMode() throws ExecutionException {
    final BazelTestFields fields = new FakeBazelTestFields(
      BazelTestFields.forTarget("//foo:test", null),
      "scripts/daemon.sh",
      "scripts/doctor.sh",
      "scripts/launch.sh",
      null,
      null,
      null
    );
    final GeneralCommandLine launchCommand = fields.getLaunchCommand(projectFixture.getProject(), RunMode.DEBUG);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("/workspace/scripts/launch.sh");
    expectedCommandLine.add("//foo:test");
    expectedCommandLine.add("--");
    expectedCommandLine.add("--enable-debugging");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void failsForFileWithoutTestScript() {
    final BazelTestFields fields = new FakeBazelTestFields(
      BazelTestFields.forFile("/workspace/foo/test/foo_test.dart", null),
      "scripts/daemon.sh",
      "scripts/doctor.sh",
      "scripts/launch.sh",
      null,
      null,
      null
    );
    boolean didThrow = false;
    try {
      final GeneralCommandLine launchCommand = fields.getLaunchCommand(projectFixture.getProject(), RunMode.RUN);
    }
    catch (ExecutionException e) {
      didThrow = true;
    }
    assertTrue("This test method expected to throw an exception, but did not.", didThrow);
  }

  @Test
  public void failsForTestNameWithoutTestScript() {
    final BazelTestFields fields = new FakeBazelTestFields(
      BazelTestFields.forTestName("first test", "/workspace/foo/test/foo_test.dart", null),
      "scripts/daemon.sh",
      "scripts/doctor.sh",
      "scripts/launch.sh",
      null,
      null,
      null
    );
    boolean didThrow = false;
    try {
      final GeneralCommandLine launchCommand = fields.getLaunchCommand(projectFixture.getProject(), RunMode.RUN);
    }
    catch (ExecutionException e) {
      didThrow = true;
    }
    assertTrue("This test method expected to throw an exception, but did not.", didThrow);
  }

  @Test
  public void runsInFileModeWhenBothFileAndBazelTargetAreProvided() throws ExecutionException {
    final BazelTestFields fields = new FakeBazelTestFields(
      new BazelTestFields(null, "/workspace/foo/test/foo_test.dart", "//foo:test", "--arg1 --arg2 3")
    );
    final GeneralCommandLine launchCommand = fields.getLaunchCommand(projectFixture.getProject(), RunMode.RUN);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("/workspace/scripts/flutter-test.sh");
    expectedCommandLine.add("--arg1");
    expectedCommandLine.add("--arg2");
    expectedCommandLine.add("3");
    expectedCommandLine.add("--no-color");
    expectedCommandLine.add("foo/test/foo_test.dart");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  @Test
  public void runsInBazelTargetModeWhenBothFileAndBazelTargetAreProvidedWithoutTestScript() throws ExecutionException {
    final BazelTestFields fields = new FakeBazelTestFields(
      new BazelTestFields(null, "/workspace/foo/test/foo_test.dart", "//foo:test", "--ignored-args"),
      "scripts/daemon.sh",
      "scripts/doctor.sh",
      "scripts/launch.sh",
      null,
      null,
      null
    );
    final GeneralCommandLine launchCommand = fields.getLaunchCommand(projectFixture.getProject(), RunMode.RUN);

    final List<String> expectedCommandLine = new ArrayList<>();
    expectedCommandLine.add("/workspace/scripts/launch.sh");
    expectedCommandLine.add("//foo:test");
    assertThat(launchCommand.getCommandLineList(null), equalTo(expectedCommandLine));
  }

  private FakeBazelTestFields forFile(String file) {
    return new FakeBazelTestFields(BazelTestFields.forFile(file, null));
  }

  private FakeBazelTestFields forTestName(String testName, String file) {
    return new FakeBazelTestFields(BazelTestFields.forTestName(testName, file, null));
  }

  private FakeBazelTestFields forTarget(String target) {
    return new FakeBazelTestFields(BazelTestFields.forTarget(target, null));
  }

  /**
   * Fake bazel test fields that doesn't depend on the Dart SDK.
   */
  private static class FakeBazelTestFields extends BazelTestFields {

    final MockVirtualFileSystem fs;
    final Workspace fakeWorkspace;

    FakeBazelTestFields(@NotNull BazelTestFields template,
                        @Nullable String daemonScript,
                        @Nullable String doctorScript,
                        @Nullable String launchScript,
                        @Nullable String testScript,
                        @Nullable String sdkHome,
                        @Nullable String versionFile) {
      super(template);
      final Pair.NonNull<MockVirtualFileSystem, Workspace> pair = FakeWorkspaceFactory
        .createWorkspaceAndFilesystem(daemonScript, doctorScript, launchScript, testScript, sdkHome, versionFile);
      fs = pair.first;
      fakeWorkspace = pair.second;
    }

    FakeBazelTestFields(@NotNull BazelTestFields template) {
      super(template);
      final Pair.NonNull<MockVirtualFileSystem, Workspace> pair = FakeWorkspaceFactory.createWorkspaceAndFilesystem();
      fs = pair.first;
      fakeWorkspace = pair.second;
    }

    @Override
    void checkRunnable(@NotNull Project project) throws RuntimeConfigurationError {
      // Skip the Dart VM check in test code.
      getScope(project).checkRunnable(this, project);
    }

    @Nullable
    @Override
    protected Workspace getWorkspace(@NotNull Project project) {
      return fakeWorkspace;
    }

    @Override
    protected void verifyMainFile(Project project) throws RuntimeConfigurationError {
      // We don't have access to the filesystem in tests, so don't verify anything.
    }
  }
}
