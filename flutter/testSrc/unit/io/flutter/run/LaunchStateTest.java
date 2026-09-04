/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class LaunchStateTest {

  @Test
  public void displaySetterIsAvailable() {
    assertNotNull(
      "setDisplayName not found on RunContentDescriptor — JetBrains may have removed or renamed it",
      LaunchState.getDisplaySetter()
    );
  }
}
