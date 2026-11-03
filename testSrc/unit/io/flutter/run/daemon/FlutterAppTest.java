/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.daemon;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import io.flutter.run.FlutterDevice;
import io.flutter.run.common.RunMode;
import io.flutter.settings.FlutterSettings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FlutterAppTest {
  private Disposable parent;
  private ProcessHandler process;
  private GeneralCommandLine command;
  private FlutterApp app;

  @Before
  public void setUp() {
    parent = Disposer.newDisposable();
    process = mock(ProcessHandler.class);
    command = new GeneralCommandLine(
      "/Users/secret_user/flutter/bin/flutter", "run", "--machine", "-d", "macos", "lib/main.dart");
    app = new FlutterApp(mock(Project.class), RunMode.RUN, mock(FlutterDevice.class), process,
                         mock(ExecutionEnvironment.class), new DaemonApi(c -> {}), command);
    Disposer.register(parent, app);
  }

  @After
  public void tearDown() {
    FlutterSettings.setInstance(null);
    Disposer.dispose(parent);
  }

  @Test
  public void disposingAppDestroysProcessAndKeepsParent() {
    Disposer.dispose(app);

    verify(process).destroyProcess();
    assertFalse(Disposer.isDisposed(parent));
  }

  @Test
  public void disposingParentDisposesApp() {
    Disposer.dispose(parent);

    assertTrue(Disposer.isDisposed(app));
    verify(process).destroyProcess();
  }

  @Test
  public void getCommandLogRedactsSdkPathWhenDisabled() {
    final FlutterSettings mockSettings = mock(FlutterSettings.class);
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(false);
    FlutterSettings.setInstance(mockSettings);

    final String log = app.getCommandLog();
    assertEquals("flutter [run, --machine, -d, macos, lib/main.dart]", log);
    assertFalse(log.contains("/Users/secret_user"));
  }

  @Test
  public void getCommandLogIncludesSdkPathWhenEnabled() {
    final FlutterSettings mockSettings = mock(FlutterSettings.class);
    when(mockSettings.isFilePathLoggingEnabled()).thenReturn(true);
    FlutterSettings.setInstance(mockSettings);

    final String log = app.getCommandLog();
    assertEquals(command.toString(), log);
    assertTrue(log.contains("/Users/secret_user/flutter/bin/flutter"));
  }
}
