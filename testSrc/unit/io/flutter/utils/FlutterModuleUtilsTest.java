/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.openapi.module.Module;
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
  public final ProjectFixture fixture = Testing.makeEmptyModule();

  @Test
  public void isDeprecatedFlutterModuleType_true() {
    fixture.getModule().setOption(Module.ELEMENT_TYPE, "WEB_MODULE");
    assertEquals(FlutterModuleUtils.DEPRECATED_FLUTTER_MODULE_TYPE_ID, fixture.getModule().getOptionValue("type"));
    // We would like to use this assert but the pub roots are not setup so
    // this assert fails.
    // TODO(jacobr): configure the pub roots correctly so this test can run
    // as intended or remove this test as it is validating obsolete behavior.
    // assertTrue(FlutterModuleUtils.isDeprecatedFlutterModuleType(fixture.getModule()));
  }

  @Test
  public void isDeprecatedFlutterModuleType_false_JAVA_MODULE() {
    fixture.getModule().setOption(Module.ELEMENT_TYPE, FlutterModuleUtils.getModuleTypeIDForFlutter());
    assertFalse(FlutterModuleUtils.isDeprecatedFlutterModuleType(fixture.getModule()));
  }

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
