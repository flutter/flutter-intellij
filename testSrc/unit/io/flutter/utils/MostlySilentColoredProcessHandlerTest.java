/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.util.io.BaseOutputReader;
import com.intellij.util.system.OS;
import io.flutter.testing.CodeInsightProjectFixture;
import io.flutter.testing.Testing;
import org.junit.Rule;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link MostlySilentColoredProcessHandler}.
 */
public class MostlySilentColoredProcessHandlerTest {

  @Rule
  public final CodeInsightProjectFixture fixture = Testing.makeCodeInsightModule();

  private static String getJavaExecutablePath() {
    final String binary = OS.CURRENT == OS.Windows ? "java.exe" : "java";
    return Path.of(System.getProperty("java.home"), "bin", binary).toString();
  }

  @Test
  public void enablesSoftKillAndMostlySilentReaderOptions() throws Exception {
    final GeneralCommandLine cmd = new GeneralCommandLine(getJavaExecutablePath(), "-version")
      .withCharset(StandardCharsets.UTF_8)
      .withRedirectErrorStream(true);

    final MostlySilentColoredProcessHandler handler = new MostlySilentColoredProcessHandler(cmd);
    try {
      assertTrue(
        "KillableColoredProcessHandler should enable graceful soft-kill by default",
        handler.shouldKillProcessSoftly()
      );
      assertEquals(BaseOutputReader.Options.forMostlySilentProcess(), handler.readerOptions());
      handler.startNotify();
      assertTrue("Process should finish within timeout", handler.waitFor(10_000));
    }
    finally {
      if (!handler.isProcessTerminated()) {
        handler.destroyProcess();
        handler.waitFor(5_000);
      }
    }
  }

  @Test
  public void deliversProcessOutputToCallback() throws Exception {
    final GeneralCommandLine cmd = new GeneralCommandLine(getJavaExecutablePath(), "-version")
      .withCharset(StandardCharsets.UTF_8)
      .withRedirectErrorStream(true);

    final List<String> chunks = new CopyOnWriteArrayList<>();
    final MostlySilentColoredProcessHandler handler =
      new MostlySilentColoredProcessHandler(cmd, chunks::add);
    try {
      handler.startNotify();
      assertTrue("Process should finish within timeout", handler.waitFor(10_000));
      final String combined = String.join("", chunks).toLowerCase(Locale.ROOT);
      assertTrue("Expected java -version output in callback, got: " + combined, combined.contains("version"));
    }
    finally {
      if (!handler.isProcessTerminated()) {
        handler.destroyProcess();
        handler.waitFor(5_000);
      }
    }
  }

  @Test
  public void destroyProcessTerminatesRunningSubprocessGracefully() throws Exception {
    final GeneralCommandLine cmd = OS.CURRENT == OS.Windows
      ? new GeneralCommandLine("ping", "-n", "30", "127.0.0.1")
      : new GeneralCommandLine("sleep", "30");
    cmd.withCharset(StandardCharsets.UTF_8);

    final MostlySilentColoredProcessHandler handler = new MostlySilentColoredProcessHandler(cmd);
    try {
      handler.startNotify();
      handler.destroyProcess();
      assertTrue("Destroyed process should terminate within timeout", handler.waitFor(10_000));
      assertTrue(handler.isProcessTerminated());
    }
    finally {
      if (handler.getProcess().isAlive()) {
        handler.getProcess().destroyForcibly();
      }
    }
  }
}
