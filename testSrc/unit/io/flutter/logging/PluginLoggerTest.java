/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.openapi.diagnostic.Logger;
import io.flutter.settings.FlutterSettings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PluginLoggerTest {
  private FlutterSettings mockSettings;
  private Logger mockLogger;

  @Before
  public void setUp() {
    mockSettings = mock(FlutterSettings.class);
    mockLogger = mock(Logger.class);
    FlutterSettings.setInstance(mockSettings);
  }

  @After
  public void tearDown() {
    FlutterSettings.setInstance(null);
  }

  @Test
  public void testIsFilePathLoggingEnabled_whenEnabled() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(true);
    assertTrue(PluginLogger.isFilePathLoggingEnabled());
  }

  @Test
  public void testIsFilePathLoggingEnabled_whenDisabled() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(false);
    assertFalse(PluginLogger.isFilePathLoggingEnabled());
  }

  @Test
  public void testIsFilePathLoggingEnabled_whenSettingsNull_defensivelyReturnsFalse() {
    FlutterSettings.setInstance(null);
    assertFalse(PluginLogger.isFilePathLoggingEnabled());
  }

  @Test
  public void testInfoFilePath_string_whenDisabled() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(false);
    PluginLogger.infoFilePath(mockLogger, "/secret/path/file.dart");
    verify(mockLogger, never()).info(anyString());
  }

  @Test
  public void testInfoFilePath_string_whenEnabled() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(true);
    PluginLogger.infoFilePath(mockLogger, "/allowed/path/file.dart");
    verify(mockLogger).info("/allowed/path/file.dart");
  }

  @Test
  public void testInfoFilePath_supplier_whenDisabled_doesNotEvaluateSupplier() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(false);
    final AtomicBoolean supplierEvaluated = new AtomicBoolean(false);

    PluginLogger.infoFilePath(mockLogger, () -> {
      supplierEvaluated.set(true);
      return "expensive message with /path";
    });

    assertFalse("Supplier should not be evaluated when path logging is disabled", supplierEvaluated.get());
    verify(mockLogger, never()).info(anyString());
  }

  @Test
  public void testInfoFilePath_supplier_whenEnabled_evaluatesSupplierOnce() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(true);
    final AtomicInteger evaluationCount = new AtomicInteger(0);

    PluginLogger.infoFilePath(mockLogger, () -> {
      evaluationCount.incrementAndGet();
      return "message with /path";
    });

    assertEquals("Supplier should be evaluated exactly once", 1, evaluationCount.get());
    verify(mockLogger).info("message with /path");
  }

  @Test
  public void testInfoFilePath_throwable_whenDisabled_omitsThrowable() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(false);
    final Throwable ex = new RuntimeException("failure in /path/to/script.dart");

    PluginLogger.infoFilePath(mockLogger, "Error reading file", ex);

    verify(mockLogger).info("Error reading file");
    verify(mockLogger, never()).info(eq("Error reading file"), any(Throwable.class));
  }

  @Test
  public void testInfoFilePath_throwable_whenEnabled_includesThrowable() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(true);
    final Throwable ex = new RuntimeException("failure in /path/to/script.dart");

    PluginLogger.infoFilePath(mockLogger, "Error reading file", ex);

    verify(mockLogger).info("Error reading file", ex);
  }

  @Test
  public void testInfoFilePath_onlyThrowable_whenDisabled() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(false);
    final Throwable ex = new RuntimeException("sanitized message");

    PluginLogger.infoFilePath(mockLogger, ex);

    verify(mockLogger).info("sanitized message");
    verify(mockLogger, never()).info(ex);
  }

  @Test
  public void testInfoFilePath_onlyThrowable_whenEnabled() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(true);
    final Throwable ex = new RuntimeException("full exception");

    PluginLogger.infoFilePath(mockLogger, ex);

    verify(mockLogger).info(ex);
  }

  @Test
  public void testWarnFilePath_string_whenDisabled() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(false);
    PluginLogger.warnFilePath(mockLogger, "warning with /file/path");
    verify(mockLogger, never()).warn(anyString());
  }

  @Test
  public void testWarnFilePath_string_whenEnabled() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(true);
    PluginLogger.warnFilePath(mockLogger, "warning with /file/path");
    verify(mockLogger).warn("warning with /file/path");
  }

  @Test
  public void testWarnFilePath_supplier_whenDisabled_doesNotEvaluateSupplier() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(false);
    final AtomicBoolean supplierEvaluated = new AtomicBoolean(false);

    PluginLogger.warnFilePath(mockLogger, () -> {
      supplierEvaluated.set(true);
      return "expensive warn message";
    });

    assertFalse("Supplier should not be evaluated when path logging is disabled", supplierEvaluated.get());
    verify(mockLogger, never()).warn(anyString());
  }

  @Test
  public void testWarnFilePath_supplier_whenEnabled_evaluatesSupplierOnce() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(true);
    final AtomicInteger evaluationCount = new AtomicInteger(0);

    PluginLogger.warnFilePath(mockLogger, () -> {
      evaluationCount.incrementAndGet();
      return "warn message";
    });

    assertEquals(1, evaluationCount.get());
    verify(mockLogger).warn("warn message");
  }

  @Test
  public void testWarnFilePath_throwable_whenDisabled_omitsThrowable() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(false);
    final Throwable ex = new RuntimeException("stack trace with paths");

    PluginLogger.warnFilePath(mockLogger, "Failed to load colors", ex);

    verify(mockLogger).warn("Failed to load colors");
    verify(mockLogger, never()).warn(eq("Failed to load colors"), any(Throwable.class));
  }

  @Test
  public void testWarnFilePath_throwable_whenEnabled_includesThrowable() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(true);
    final Throwable ex = new RuntimeException("stack trace with paths");

    PluginLogger.warnFilePath(mockLogger, "Failed to load colors", ex);

    verify(mockLogger).warn("Failed to load colors", ex);
  }

  @Test
  public void testWarnFilePath_onlyThrowable_whenDisabled() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(false);
    final Throwable ex = new RuntimeException("warn message");

    PluginLogger.warnFilePath(mockLogger, ex);

    verify(mockLogger).warn("warn message");
    verify(mockLogger, never()).warn(ex);
  }

  @Test
  public void testWarnFilePath_onlyThrowable_whenEnabled() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(true);
    final Throwable ex = new RuntimeException("warn exception");

    PluginLogger.warnFilePath(mockLogger, ex);

    verify(mockLogger).warn(ex);
  }

  @Test
  public void testErrorFilePath_string_whenDisabled() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(false);
    PluginLogger.errorFilePath(mockLogger, "error with /file/path");
    verify(mockLogger, never()).error(anyString());
  }

  @Test
  public void testErrorFilePath_string_whenEnabled() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(true);
    PluginLogger.errorFilePath(mockLogger, "error with /file/path");
    verify(mockLogger).error("error with /file/path");
  }

  @Test
  public void testErrorFilePath_supplier_whenDisabled_doesNotEvaluateSupplier() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(false);
    final AtomicBoolean supplierEvaluated = new AtomicBoolean(false);

    PluginLogger.errorFilePath(mockLogger, () -> {
      supplierEvaluated.set(true);
      return "expensive error message";
    });

    assertFalse("Supplier should not be evaluated when path logging is disabled", supplierEvaluated.get());
    verify(mockLogger, never()).error(anyString());
  }

  @Test
  public void testErrorFilePath_supplier_whenEnabled_evaluatesSupplierOnce() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(true);
    final AtomicInteger evaluationCount = new AtomicInteger(0);

    PluginLogger.errorFilePath(mockLogger, () -> {
      evaluationCount.incrementAndGet();
      return "error message";
    });

    assertEquals(1, evaluationCount.get());
    verify(mockLogger).error("error message");
  }

  @Test
  public void testErrorFilePath_throwable_whenDisabled_omitsThrowable() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(false);
    final Throwable ex = new RuntimeException("critical path error");

    PluginLogger.errorFilePath(mockLogger, "Critical failure", ex);

    verify(mockLogger).error("Critical failure");
    verify(mockLogger, never()).error(eq("Critical failure"), any(Throwable.class));
  }

  @Test
  public void testErrorFilePath_throwable_whenEnabled_includesThrowable() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(true);
    final Throwable ex = new RuntimeException("critical path error");

    PluginLogger.errorFilePath(mockLogger, "Critical failure", ex);

    verify(mockLogger).error("Critical failure", ex);
  }

  @Test
  public void testErrorFilePath_onlyThrowable_whenDisabled() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(false);
    final Throwable ex = new RuntimeException("error message");

    PluginLogger.errorFilePath(mockLogger, ex);

    verify(mockLogger).error("error message");
    verify(mockLogger, never()).error(ex);
  }

  @Test
  public void testErrorFilePath_onlyThrowable_whenEnabled() {
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(true);
    final Throwable ex = new RuntimeException("error exception");

    PluginLogger.errorFilePath(mockLogger, ex);

    verify(mockLogger).error(ex);
  }
}
