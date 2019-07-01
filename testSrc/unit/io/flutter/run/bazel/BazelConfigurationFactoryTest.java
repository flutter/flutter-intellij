/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazel;

import io.flutter.testing.ProjectFixture;
import io.flutter.testing.Testing;
import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;


/**
 * Verify the behavior of bazel run configuration factories.
 *
 * <p>
 * These tests validate preconditions from Bazel IntelliJ plugin logic for how run configurations are saved to Piper.
 * If these tests fail, you may need to update some g3 code to prevent breaking g3 Bazel run configurations.
 */
public class BazelConfigurationFactoryTest {
  final FlutterBazelRunConfigurationType type = new FlutterBazelRunConfigurationType();

  @Rule
  public ProjectFixture projectFixture = Testing.makeEmptyModule();

  @Test
  public void factoryIdIsCorrect() {
    // Bazel code assumes the id of the factory as a precondition.
    assertThat(type.factory.getId(), equalTo("Flutter (Bazel)"));
  }

  @Test
  public void factoryConfigTypeMatches() {
    assertThat(type.getId(), equalTo("FlutterBazelRunConfigurationType"));
  }
}
