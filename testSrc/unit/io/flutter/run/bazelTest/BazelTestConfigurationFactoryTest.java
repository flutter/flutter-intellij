/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * Verify the behavior of bazel test configuration factories.
 *
 * <p>
 * These tests validate preconditions from Bazel IntelliJ plugin logic for how run configurations are saved to Piper.
 * If these tests fail, you may need to update some g3 code to prevent breaking g3 Bazel run configurations.
 */
public class BazelTestConfigurationFactoryTest {
  final FlutterBazelTestConfigurationType type = new FlutterBazelTestConfigurationType();

  @Test
  public void factoryIdsAreCorrect() {
    // Bazel code assumes the id of the factory as a precondition.
    assertThat(type.factory.getId(), equalTo("Flutter Test (Bazel)"));
    assertThat(type.watchFactory.getId(), equalTo("Watch Flutter Test (Bazel)"));
  }

  @Test
  public void factoryConfigTypesMatch() {
    assertThat(type.getId(), equalTo("FlutterBazelTestConfigurationType"));
  }
}
