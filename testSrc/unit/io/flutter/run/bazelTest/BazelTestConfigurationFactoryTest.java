/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import io.flutter.testing.ProjectFixture;
import io.flutter.testing.Testing;
import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * Verify the behavior of bazel test configuration factories.
 *
 * <p>
 * These tests assume preconditions from Bazel IntelliJ plugin logic for how run configurations are saved to Piper.
 */
public class BazelTestConfigurationFactoryTest {
  final FlutterBazelTestConfigurationType type = new FlutterBazelTestConfigurationType();

  @Rule
  public ProjectFixture projectFixture = Testing.makeEmptyModule();

  @Test
  public void normalFactoryIdIsCorrect() {
    // Bazel code assumes the id of this factory as a precondition.
    assertThat(type.factory.getId(), equalTo("Flutter Test (Bazel)"));
  }

  @Test
  public void watchFactoryIdIsCorrect() {
    // Bazel code assumes the id of this factory as a precondition.
    assertThat(type.watchFactory.getId(), equalTo("Watch Flutter Test (Bazel)"));
  }
}
