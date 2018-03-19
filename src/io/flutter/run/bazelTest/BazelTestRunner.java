/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import com.google.gson.*;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.*;
import com.jetbrains.lang.dart.ide.runner.ObservatoryConnector;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import gnu.trove.THashSet;
import io.flutter.run.PositionMapper;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.StdoutJsonParser;
import org.dartlang.vm.service.element.ScriptRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * The Bazel version of the {@link io.flutter.run.test.DebugTestRunner}. Runs a Bazel Flutter test configuration in the debugger.
 */
public class BazelTestRunner extends GenericProgramRunner {

  private static final Logger LOG = Logger.getInstance(BazelTestRunner.class);

  @NotNull
  @Override
  public String getRunnerId() {
    return "FlutterBazelTestRunner";
  }

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    // TODO(jwren) not sure what we want here, go look at DebugTestRunner.canRun
    return true;
  }

  @Nullable
  @Override
  protected RunContentDescriptor doExecute(@NotNull RunProfileState state, @NotNull ExecutionEnvironment env)
    throws ExecutionException {
    return runInDebugger((BazelTestLaunchState)state, env);
  }

  protected RunContentDescriptor runInDebugger(@NotNull BazelTestLaunchState launcher, @NotNull ExecutionEnvironment env)
    throws ExecutionException {
    // Start process and create console.
    final ExecutionResult executionResult = launcher.execute(env.getExecutor(), this);
    final Connector connector = new Connector(executionResult.getProcessHandler());

    //// Set up source file mapping.
    final DartUrlResolver resolver = DartUrlResolver.getInstance(env.getProject(), launcher.getTestFile());
    final PositionMapper.Analyzer analyzer = PositionMapper.Analyzer.create(env.getProject(), launcher.getTestFile());
    final BazelPositionMapper mapper =
      new BazelPositionMapper(env.getProject(), env.getProject().getBaseDir()/*this is different, incorrect?*/, resolver, analyzer,
                              connector);

    // Create the debug session.
    final XDebuggerManager manager = XDebuggerManager.getInstance(env.getProject());
    final XDebugSession session = manager.startSession(env, new XDebugProcessStarter() {
      @Override
      @NotNull
      public XDebugProcess start(@NotNull final XDebugSession session) {
        return new BazelTestDebugProcess(env, session, executionResult, resolver, connector, mapper);
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
    private String runfilesDir;
    private String workspaceDirName;

    public Connector(ProcessHandler handler) {
      listener = new ProcessAdapter() {
        @Override
        public void onTextAvailable(ProcessEvent event, Key outputType) {
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
        public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
          handler.removeProcessListener(listener);
        }
      };
      handler.addProcessListener(listener);
    }

    @Nullable
    @Override
    public String getWebSocketUrl() {
      if (observatoryUri == null || !observatoryUri.startsWith("http:") || !observatoryUri.endsWith("/")) {
        return null;
      }
      return observatoryUri.replace("http:", "ws:") + "ws";
    }

    @Nullable
    @Override
    public String getBrowserUrl() {
      return observatoryUri;
    }

    @Nullable
    public String getRunfilesDir() {
      return runfilesDir;
    }

    @Nullable
    public String getWorkspaceDirName() {
      return workspaceDirName;
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
        final JsonParser jp = new JsonParser();
        final JsonElement elem = jp.parse(json);
        obj = elem.getAsJsonObject();
      }
      catch (JsonSyntaxException e) {
        LOG.error("Unable to parse JSON from Flutter test", e);
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
        LOG.error("Missing event field in JSON from Flutter test: " + obj);
        return;
      }

      final String eventName = primEvent.getAsString();
      if (eventName == null) {
        LOG.error("Unexpected event field in JSON from Flutter test: " + obj);
        return;
      }

      final JsonObject params = obj.getAsJsonObject("params");
      if (params == null) {
        LOG.error("Missing parameters in event from Flutter test: " + obj);
        return;
      }

      if (eventName.equals("test.startedProcess")) {
        final JsonPrimitive primUri = params.getAsJsonPrimitive("observatoryUri");
        if (primUri != null) {
          observatoryUri = primUri.getAsString();
        }
        final JsonPrimitive primRunfilesDir = params.getAsJsonPrimitive("runfilesDir");
        if (primRunfilesDir != null) {
          runfilesDir = primRunfilesDir.getAsString();
        }
        final JsonPrimitive primWorkspaceDirName = params.getAsJsonPrimitive("workspaceDirName");
        if (primWorkspaceDirName != null) {
          workspaceDirName = primWorkspaceDirName.getAsString();
        }
      }
    }
  }

  private static final class BazelPositionMapper extends PositionMapper {

    @NotNull final Connector connector;

    @NotNull Set<String> workspaceLocations = new THashSet<>(1);

    public static final String RUNFILES_SLASH = "runfiles/";

    public BazelPositionMapper(@NotNull final Project project,
                               @NotNull final VirtualFile sourceRoot,
                               @NotNull final DartUrlResolver resolver,
                               @Nullable final Analyzer analyzer,
                               @NotNull final Connector connector) {
      super(project, sourceRoot, resolver, analyzer);
      this.connector = connector;
    }

    @NotNull
    public Collection<String> getBreakpointUris(@NotNull final VirtualFile file) {
      // Get the results from superclass
      final Collection<String> results = super.getBreakpointUris(file);

      // Get the runfiles directory and workspace directory name provided by the test harness.
      final String runfilesDir = connector.getRunfilesDir();
      final String workspaceDirName = connector.getWorkspaceDirName();

      // Verify the returned runfiles directory and workspace directory name
      if (!verifyRunFilesAndWorkspaceNameValues(runfilesDir, workspaceDirName)) {
        return results;
      }

      final String filePath = file.getPath();
      final int workspaceOffset = filePath.lastIndexOf(workspaceDirName + "/");
      if (workspaceOffset != -1) {
        // Append the passed runfilesDir + path from workspace to the returned results, this will set an additional breakpoint in the
        // generated runfiles directory
        results.add(runfilesDir + filePath.substring(workspaceOffset, filePath.length()));

        // Finally, add the path to the workspace to workspaceLocations so that the original path can be computed in getSourcePosition
        // method below
        workspaceLocations.add(filePath.substring(0, workspaceOffset));
      }
      return results;
    }

    /**
     * Returns the local position (to display to the user) corresponding to a token position in Observatory.
     */
    @Override
    @Nullable
    public XSourcePosition getSourcePosition(@NotNull final String isolateId, @NotNull final ScriptRef scriptRef, int tokenPos) {
      final XSourcePosition xSourcePosition = super.getSourcePosition(isolateId, scriptRef, tokenPos);
      if (xSourcePosition == null) return null;

      // Get the runfiles directory and workspace directpory name provided by the test harness.
      final String runfilesDir = connector.getRunfilesDir();
      final String workspaceDirName = connector.getWorkspaceDirName();

      // Verify the returned runfiles directory and workspace directory name
      if (!verifyRunFilesAndWorkspaceNameValues(runfilesDir, workspaceDirName)) return xSourcePosition;

      // Get the virtual file computed by the super.getSourcePosition(...)
      final VirtualFile superVirtualFile = xSourcePosition.getFile();
      final String filePath = superVirtualFile.getPath();
      final int workspaceOffset = filePath.lastIndexOf(workspaceDirName + "/");
      if (filePath.startsWith(runfilesDir) && workspaceOffset != -1) {
        for (String workspaceLoc : workspaceLocations) {
          final VirtualFile virtualFileInProject =
            LocalFileSystem.getInstance().findFileByPath(workspaceLoc + filePath.substring(workspaceOffset, filePath.length()));
          if (virtualFileInProject != null) {
            return XDebuggerUtil.getInstance().createPosition(virtualFileInProject, xSourcePosition.getLine(), xSourcePosition.getOffset());
          }
        }
      }
      return xSourcePosition;
    }

    /**
     * Return true if the passed runfilesDir and workspaceDirName are not null, are not empty, and the runfilesDir ends with
     * {@value RUNFILES_SLASH}.
     */
    private boolean verifyRunFilesAndWorkspaceNameValues(@Nullable final String runfilesDir, @Nullable final String workspaceDirName) {
      return runfilesDir != null && runfilesDir.endsWith(RUNFILES_SLASH) && workspaceDirName != null && !workspaceDirName.isEmpty();
    }
  }
}
