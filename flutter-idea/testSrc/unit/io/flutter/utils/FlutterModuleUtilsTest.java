/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import io.flutter.testing.ProjectFixture;
import io.flutter.testing.Testing;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Verifies the Flutter module detection utilities in {@link FlutterModuleUtils}.
 */
public class FlutterModuleUtilsTest {

  @Rule
  public final ProjectFixture<IdeaProjectTestFixture> fixture = Testing.makeEmptyModule();

  @Test
  public void isDeprecatedFlutterModuleType_false_empty_module() {
    assertFalse(FlutterModuleUtils.isDeprecatedFlutterModuleType(fixture.getModule()));
  }

  @Test
  public void isFlutterModule_null() {
    assertFalse(FlutterModuleUtils.isFlutterModule(null));
  }

  @Test
  public void isFlutterModule_emptyModule() {
    assertFalse(FlutterModuleUtils.isFlutterModule(fixture.getModule()));
  }
}
