/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import io.flutter.testing.CodeInsightProjectFixture;
import io.flutter.testing.Testing;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.SwingUtilities;

import static org.junit.Assert.assertNotNull;

/**
 * Verifies {@link EnableDartSupportForModule}.
 */
public class EnableDartSupportForModuleTest {

  @Rule
  public final CodeInsightProjectFixture fixture = Testing.makeCodeInsightModule();

  @Test
  public void testRunDoesNotThrowOrRequireServerReady() throws Exception {
    assertNotNull(fixture.getModule());
    final EnableDartSupportForModule runnable = new EnableDartSupportForModule(fixture.getModule());
    runnable.run();
    // Flush the EDT event queue (FIFO) to ensure the nested invokeLater blocks execute
    // before fixture teardown. In IntelliJ tests, unhandled exceptions on the EDT during
    // event dispatch are tracked by the test harness and will fail the test.
    SwingUtilities.invokeAndWait(() -> {});
    SwingUtilities.invokeAndWait(() -> {});
  }

  @Test
  public void testMultipleRunsAreIdempotent() throws Exception {
    assertNotNull(fixture.getModule());
    final EnableDartSupportForModule runnable = new EnableDartSupportForModule(fixture.getModule());
    runnable.run();
    runnable.run();
    // Flush the EDT event queue (FIFO) to ensure multiple invocations execute cleanly
    // before fixture teardown.
    SwingUtilities.invokeAndWait(() -> {});
    SwingUtilities.invokeAndWait(() -> {});
  }
}
