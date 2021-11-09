/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.runners.RunContentBuilder;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.util.TimeoutUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import io.flutter.FlutterUtils;
import io.flutter.ObservatoryConnector;
import io.flutter.run.FlutterPositionMapper;
import io.flutter.run.common.CommonTestConfigUtils;
import io.flutter.sdk.FlutterSdk;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.JsonUtils;
import io.flutter.utils.StdoutJsonParser;
import io.flutter.utils.VmServiceListenerAdapter;
import io.flutter.vmService.VmServiceConsumers;
import io.flutter.vmService.VmServiceConsumers.EmptyResumeConsumer;
import org.dartlang.vm.service.VmService;
import org.dartlang.vm.service.consumer.VMConsumer;
import org.dartlang.vm.service.element.ElementList;
import org.dartlang.vm.service.element.Event;
import org.dartlang.vm.service.element.EventKind;
import org.dartlang.vm.service.element.Isolate;
import org.dartlang.vm.service.element.IsolateRef;
import org.dartlang.vm.service.element.RPCError;
import org.dartlang.vm.service.element.VM;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Runs a Flutter test configuration in the debugger.
 */
public class FlutterTestRunner extends GenericProgramRunner {
  @NotNull
  @Override
  public String getRunnerId() {
    return "FlutterDebugTestRunner";
  }

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    if (!(DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) || ToolWindowId.RUN.equals(executorId)) || !(profile instanceof TestConfig)) {
      return false;
    }

    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(((TestConfig)profile).getProject());
    if (sdk == null || !sdk.getVersion().flutterTestSupportsMachineMode()) {
      return false;
    }

    final TestConfig config = (TestConfig)profile;
    return config.getFields().getScope() != TestFields.Scope.DIRECTORY;
  }

  @Nullable
  @Override
  protected RunContentDescriptor doExecute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment env)
    throws ExecutionException {
    if (env.getExecutor().getId().equals(ToolWindowId.RUN)) {
      return run((TestLaunchState)state, env);
    }
    else {
      return runInDebugger((TestLaunchState)state, env);
    }
  }

  protected RunContentDescriptor run(@NotNull TestLaunchState launcher, @NotNull ExecutionEnvironment env)
    throws ExecutionException {
    final ExecutionResult executionResult = launcher.execute(env.getExecutor(), this);
    final ObservatoryConnector connector = new Connector(executionResult.getProcessHandler());

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      // Poll, waiting for "flutter run" to give us a websocket.
      // This is adapted from DartVmServiceDebugProcess::scheduleConnect.
      String url = connector.getWebSocketUrl();

      while (url == null) {
        if (launcher.isTerminated()) {
          return;
        }

        TimeoutUtil.sleep(100);

        url = connector.getWebSocketUrl();
      }

      if (launcher.isTerminated()) {
        return;
      }

      final VmService vmService;

      try {
        vmService = VmService.connect(url);
      }
      catch (IOException | RuntimeException e) {
        if (!launcher.isTerminated()) {
          launcher.notifyTextAvailable(
            "Failed to connect to the VM service at: " + url + "\n" + e.toString() + "\n",
            ProcessOutputTypes.STDERR);
        }
        return;
      }

      // Listen for debug 'PauseStart' events for isolates after the initial connect and resume those isolates.
      vmService.streamListen(VmService.DEBUG_STREAM_ID, VmServiceConsumers.EMPTY_SUCCESS_CONSUMER);
      vmService.addVmServiceListener(new VmServiceListenerAdapter() {
        @Override
        public void received(String streamId, Event event) {
          if (EventKind.PauseStart.equals(event.getKind())) {
            resumePausedAtStartIsolate(launcher, vmService, event.getIsolate());
          }
        }
      });

      // Resume any isolates paused at the initial connect.
      vmService.getVM(new VMConsumer() {
        @Override
        public void received(VM response) {
          final ElementList<IsolateRef> isolates = response.getIsolates();
          for (IsolateRef isolateRef : isolates) {
            resumePausedAtStartIsolate(launcher, vmService, isolateRef);
          }
        }

        @Override
        public void onError(RPCError error) {
          if (!launcher.isTerminated()) {
            launcher.notifyTextAvailable(
              "Error connecting to VM: " + error.getCode() + " " + error.getMessage() + "\n",
              ProcessOutputTypes.STDERR);
          }
        }
      });
    });

    return new RunContentBuilder(executionResult, env).showRunContent(env.getContentToReuse());
  }

  private void resumePausedAtStartIsolate(@NotNull TestLaunchState launcher, @NotNull VmService vmService, @NotNull IsolateRef isolateRef) {
    if (isolateRef.getIsSystemIsolate()) {
      return;
    }

    vmService.getIsolate(isolateRef.getId(), new VmServiceConsumers.GetIsolateConsumerWrapper() {
      @Override
      public void received(Isolate isolate) {
        final Event event = isolate.getPauseEvent();
        final EventKind eventKind = event.getKind();

        if (eventKind == EventKind.PauseStart) {
          vmService.resume(isolateRef.getId(), new EmptyResumeConsumer() {
            @Override
            public void onError(RPCError error) {
              if (!launcher.isTerminated()) {
                launcher.notifyTextAvailable(
                  "Error resuming isolate " + isolateRef.getId() + ": " + error.getCode() + " " + error.getMessage() + "\n",
                  ProcessOutputTypes.STDERR);
              }
            }
          });
        }
      }
    });
  }

  protected RunContentDescriptor runInDebugger(@NotNull TestLaunchState launcher, @NotNull ExecutionEnvironment env)
    throws ExecutionException {

    // Start process and create console.
    final ExecutionResult executionResult = launcher.execute(env.getExecutor(), this);
    final ObservatoryConnector connector = new Connector(executionResult.getProcessHandler());

    // Set up source file mapping.
    final DartUrlResolver resolver = DartUrlResolver.getInstance(env.getProject(), launcher.getTestFileOrDir());
    final FlutterPositionMapper.Analyzer analyzer = FlutterPositionMapper.Analyzer.create(env.getProject(), launcher.getTestFileOrDir());
    final FlutterPositionMapper mapper = new FlutterPositionMapper(env.getProject(), launcher.getPubRoot().getRoot(), resolver, analyzer);

    // Create the debug session.
    final XDebuggerManager manager = XDebuggerManager.getInstance(env.getProject());
    final XDebugSession session = manager.startSession(env, new XDebugProcessStarter() {
      @Override
      @NotNull
      public XDebugProcess start(@NotNull final XDebugSession session) {
        return new TestDebugProcess(env, session, executionResult, resolver, connector, mapper);
      }
    });

    return session.getRunContentDescriptor();
  }

  /**
   * Provides observatory URI, as received from the test process.
   */
  private static final class Connector implements ObservatoryConnector {
    private final StdoutJsonParser stdoutParser = new StdoutJsonParser();
    private final ProcessListener listener;
    private String observatoryUri;

    public Connector(ProcessHandler handler) {
      listener = new ProcessAdapter() {
        @Override
        public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
          if (!outputType.equals(ProcessOutputTypes.STDOUT)) {
            return;
          }

          final String text = event.getText();
          if (FlutterSettings.getInstance().isVerboseLogging()) {
            LOG.info("[<-- " + text.trim() + "]");
          }

          stdoutParser.appendOutput(text);

          for (String line : stdoutParser.getAvailableLines()) {
            if (line.startsWith("[{")) {
              line = line.trim();

              final String json = line.substring(1, line.length() - 1);
              dispatchJson(json);
            }
          }
        }

        @Override
        public void processWillTerminate(@NotNull ProcessEvent event, boolean willBeDestroyed) {
          handler.removeProcessListener(listener);
        }
      };
      handler.addProcessListener(listener);
    }

    @Nullable
    @Override
    public String getWebSocketUrl() {
      if (observatoryUri == null || !observatoryUri.startsWith("http:")) {
        return null;
      }
      return CommonTestConfigUtils.convertHttpServiceProtocolToWs(observatoryUri);
    }

    @Nullable
    @Override
    public String getBrowserUrl() {
      return observatoryUri;
    }

    @Nullable
    @Override
    public String getRemoteBaseUrl() {
      return null;
    }

    @Override
    public void onDebuggerPaused(@NotNull Runnable resume) {
    }

    @Override
    public void onDebuggerResumed() {
    }

    private void dispatchJson(String json) {
      final JsonObject obj;
      try {
        final JsonElement elem = JsonUtils.parseString(json);
        obj = elem.getAsJsonObject();
      }
      catch (JsonSyntaxException e) {
        FlutterUtils.warn(LOG, "Unable to parse JSON from Flutter test", e);
        return;
      }

      final JsonPrimitive primId = obj.getAsJsonPrimitive("id");
      if (primId != null) {
        // Not an event.
        LOG.info("Ignored JSON from Flutter test: " + json);
        return;
      }

      final JsonPrimitive primEvent = obj.getAsJsonPrimitive("event");
      if (primEvent == null) {
        FlutterUtils.warn(LOG, "Missing event field in JSON from Flutter test: " + obj);
        return;
      }

      final String eventName = primEvent.getAsString();
      if (eventName == null) {
        FlutterUtils.warn(LOG, "Unexpected event field in JSON from Flutter test: " + obj);
        return;
      }

      final JsonObject params = obj.getAsJsonObject("params");
      if (params == null) {
        FlutterUtils.warn(LOG, "Missing parameters in event from Flutter test: " + obj);
        return;
      }

      if (eventName.equals("test.startedProcess")) {
        final JsonPrimitive primUri = params.getAsJsonPrimitive("observatoryUri");
        if (primUri != null) {
          observatoryUri = primUri.getAsString();
        }
      }
    }
  }

  private static final Logger LOG = Logger.getInstance(FlutterTestRunner.class);
}
