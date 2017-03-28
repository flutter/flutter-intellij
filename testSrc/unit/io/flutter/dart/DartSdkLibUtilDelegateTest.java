/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import io.flutter.testing.ProjectFixture;
import io.flutter.testing.Testing;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DartSdkLibUtilDelegateTest {
  @Rule
  public ProjectFixture fixture = Testing.makeEmptyModule();

  @Test
  public void enableDartSdk() throws Exception {
    final DartSdkLibUtilDelegate delegate = new DartSdkLibUtilDelegate();
    Testing.runInWriteAction(() -> delegate.enableDartSdk(fixture.getModule()));
    assertTrue(delegate.isDartSdkEnabled(fixture.getModule()));
  }

  @Test
  public void disableDartSdk() throws Exception {
    final DartSdkLibUtilDelegate delegate = new DartSdkLibUtilDelegate();
    Testing.runInWriteAction(() -> delegate.disableDartSdk(Collections.singleton(fixture.getModule())));
    assertFalse(delegate.isDartSdkEnabled(fixture.getModule()));
  }
}
