/*
 * Copyright 2026 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FlutterDebugSessionUtilsTest {

  @Test
  public void splitDebugTabNamingHooksAreAvailable() {
    final String error = FlutterDebugSessionUtils.getNamedTabSupportError();
    assertNull(
      "XDebugSessionBuilder lost the reflective hooks needed to label split debug tabs: " + error,
      error
    );
  }

  @Test
  public void splitDebugSessionUsesDeviceQualifiedBuilderTitle() throws Exception {
    final ExecutionEnvironment env = mock(ExecutionEnvironment.class);
    final RunContentDescriptor contentToReuse = mock(RunContentDescriptor.class);
    when(env.getContentToReuse()).thenReturn(contentToReuse);

    final RunContentDescriptor descriptor = mock(RunContentDescriptor.class);
    final XDebugSession session = mock(XDebugSession.class);
    final RecordingSessionBuilder builder = new RecordingSessionBuilder(session, descriptor);
    final RecordingManager manager = new RecordingManager(builder);
    final XDebugProcessStarter starter = mock(XDebugProcessStarter.class);
    final FlutterDebugSessionUtils.BuilderHooks hooks = new FlutterDebugSessionUtils.BuilderHooks(
      RecordingManager.class.getMethod("newSessionBuilder", XDebugProcessStarter.class),
      RecordingSessionBuilder.class.getMethod("environment", ExecutionEnvironment.class),
      RecordingSessionBuilder.class.getMethod("sessionName", String.class),
      RecordingSessionBuilder.class.getMethod("contentToReuse", RunContentDescriptor.class),
      RecordingSessionBuilder.class.getMethod("showTab", boolean.class),
      RecordingSessionBuilder.class.getMethod("startSession")
    );

    final RunContentDescriptor result = FlutterDebugSessionUtils.startSessionAndGetDescriptor(
      hooks,
      manager,
      env,
      starter,
      "main.dart (macOS)",
      false
    );

    assertSame(descriptor, result);
    assertSame(env, builder.environment);
    assertSame(contentToReuse, builder.contentToReuse);
    assertEquals("main.dart (macOS)", builder.sessionName);
    assertEquals(Boolean.TRUE, builder.showTab);
    assertEquals(
      List.of("newSessionBuilder", "environment", "contentToReuse", "sessionName", "showTab", "startSession"),
      builder.calls
    );
  }

  private static final class RecordingManager {
    private final RecordingSessionBuilder builder;

    private RecordingManager(RecordingSessionBuilder builder) {
      this.builder = builder;
    }

    public RecordingSessionBuilder newSessionBuilder(XDebugProcessStarter starter) {
      builder.calls.add("newSessionBuilder");
      builder.starter = starter;
      return builder;
    }
  }

  private static final class RecordingSessionBuilder {
    private final FakeSessionStartedResult startedResult;
    private final List<String> calls = new ArrayList<>();
    private XDebugProcessStarter starter;
    private ExecutionEnvironment environment;
    private String sessionName;
    private RunContentDescriptor contentToReuse;
    private Boolean showTab;

    private RecordingSessionBuilder(XDebugSession session, RunContentDescriptor descriptor) {
      startedResult = new FakeSessionStartedResult(session, descriptor);
    }

    public FakeSessionStartedResult startSession() {
      calls.add("startSession");
      return startedResult;
    }

    public RecordingSessionBuilder environment(ExecutionEnvironment environment) {
      calls.add("environment");
      this.environment = environment;
      return this;
    }

    public RecordingSessionBuilder sessionName(String sessionName) {
      calls.add("sessionName");
      this.sessionName = sessionName;
      return this;
    }

    public RecordingSessionBuilder contentToReuse(RunContentDescriptor contentToReuse) {
      calls.add("contentToReuse");
      this.contentToReuse = contentToReuse;
      return this;
    }

    public RecordingSessionBuilder showTab(boolean value) {
      calls.add("showTab");
      showTab = value;
      return this;
    }
  }

  private static final class FakeSessionStartedResult {
    private final XDebugSession session;
    private final RunContentDescriptor runContentDescriptor;

    private FakeSessionStartedResult(XDebugSession session, RunContentDescriptor runContentDescriptor) {
      this.session = session;
      this.runContentDescriptor = runContentDescriptor;
    }

    public XDebugSession getSession() {
      return session;
    }

    public RunContentDescriptor getRunContentDescriptor() {
      return runContentDescriptor;
    }
  }
}
