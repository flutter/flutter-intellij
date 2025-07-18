/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.jetbrains.lang.dart.sdk.DartSdkLibUtil;
import com.jetbrains.lang.dart.util.DartUrlResolver;
import io.flutter.FlutterMessages;
import io.flutter.FlutterUtils;
import io.flutter.ObservatoryConnector;
import io.flutter.bazel.Workspace;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.run.FlutterPositionMapper;
import io.flutter.run.common.CommonTestConfigUtils;
import io.flutter.run.test.FlutterTestRunner;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.JsonUtils;
import io.flutter.utils.OpenApiUtils;
import io.flutter.utils.StdoutJsonParser;
import io.flutter.utils.UrlUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Bazel version of the {@link FlutterTestRunner}. Runs a Bazel Flutter test configuration in the debugger.
 */
public class BazelTestRunner extends GenericProgramRunner {
  private static final @NotNull Logger LOG = Logger.getInstance(BazelTestRunner.class);

  @NotNull
  @Override
  public String getRunnerId() {
    return "FlutterBazelTestRunner";
  }

  /**
   * Only allow this runner to execute for the Run and Debug executors.
   */
  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return (DefaultRunExecutor.EXECUTOR_ID.equals(executorId) || DefaultDebugExecutor.EXECUTOR_ID.equals(executorId)) &&
           profile instanceof BazelTestConfig;
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
    final Connector connector = new Connector(executionResult.getProcessHandler(), env.getProject());

    // Set up source file mapping.
    final DartUrlResolver resolver = DartUrlResolver.getInstance(env.getProject(), launcher.getTestFile());
    final FlutterPositionMapper.Analyzer analyzer = FlutterPositionMapper.Analyzer.create(env.getProject(), launcher.getTestFile());

    final BazelPositionMapper mapper =
      new BazelPositionMapper(env.getProject(), ProjectUtil.guessProjectDir(env.getProject()), resolver, analyzer,
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
    private static final String STARTED_PROCESS = "test.startedProcess";
    private static final String OBSERVATORY_URI_KEY = "vmServiceUri";
    private static final String RUNFILES_DIR_KEY = "runfilesDir";
    private static final String WORKSPACE_DIR_NAME_KEY = "workspaceDirName";

    public Connector(ProcessHandler handler, Project project) {
      Workspace workspace = WorkspaceCache.getInstance(project).get();
      assert (workspace != null);
      String configWarningPrefix = workspace.getConfigWarningPrefix();
      listener = new ProcessAdapter() {
        @Override
        public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
          final String text = event.getText();
          if (configWarningPrefix != null && text.startsWith(configWarningPrefix)) {
            FlutterMessages.showWarning(
              "Configuration warning",
              UrlUtils.generateHtmlFragmentWithHrefTags(text.substring(configWarningPrefix.length())),
              null
            );
          }

          if (!outputType.equals(ProcessOutputTypes.STDOUT)) {
            return;
          }

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

      if (eventName.equals(STARTED_PROCESS)) {
        final JsonPrimitive primUri = params.getAsJsonPrimitive(OBSERVATORY_URI_KEY);
        if (primUri != null) {
          observatoryUri = primUri.getAsString();
        }
        final JsonPrimitive primRunfilesDir = params.getAsJsonPrimitive(RUNFILES_DIR_KEY);
        if (primRunfilesDir != null) {
          runfilesDir = primRunfilesDir.getAsString();
        }
        final JsonPrimitive primWorkspaceDirName = params.getAsJsonPrimitive(WORKSPACE_DIR_NAME_KEY);
        if (primWorkspaceDirName != null) {
          workspaceDirName = primWorkspaceDirName.getAsString();
        }
      }
    }
  }

  private static final class BazelPositionMapper extends FlutterPositionMapper {
    @NotNull final Connector connector;

    public BazelPositionMapper(@NotNull final Project project,
                               @NotNull final VirtualFile sourceRoot,
                               @NotNull final DartUrlResolver resolver,
                               @Nullable final Analyzer analyzer,
                               @NotNull final Connector connector) {
      super(project, sourceRoot, resolver, analyzer);
      this.connector = connector;
    }

    /**
     * Attempt to find a local Dart file corresponding to a script in Observatory.
     */
    @Nullable
    @Override
    protected VirtualFile findLocalFile(@NotNull final String uri) {
      // Get the workspace directory name provided by the test harness.
      final String workspaceDirName = connector.getWorkspaceDirName();

      // Verify the returned workspace directory name, we weren't passed a workspace name or if the valid workspace name does not start the
      // uri then return the super invocation of this method. This prevents the unknown URI type from being passed to the analysis server.
      if (StringUtil.isEmpty(workspaceDirName) || !uri.startsWith(workspaceDirName + ":/")) return super.findLocalFile(uri);

      final String pathFromWorkspace = uri.substring(workspaceDirName.length() + 1);

      // For each root in each module, look for a bazel workspace path, if found attempt to compute the VirtualFile, return when found.
      return OpenApiUtils.safeRunReadAction(() -> {
        for (Module module : DartSdkLibUtil.getModulesWithDartSdkEnabled(getProject())) {
          for (ContentEntry contentEntry : ModuleRootManager.getInstance(module).getContentEntries()) {
            final VirtualFile includedRoot = contentEntry.getFile();
            if (includedRoot == null) continue;

            final String includedRootPath = includedRoot.getPath();
            final int workspaceOffset = includedRootPath.indexOf(workspaceDirName);
            if (workspaceOffset == -1) continue;

            final String pathToWorkspace = includedRootPath.substring(0, workspaceOffset + workspaceDirName.length());
            final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(pathToWorkspace + pathFromWorkspace);
            if (virtualFile != null) {
              return virtualFile;
            }
          }
        }
        return super.findLocalFile(uri);
      });
    }
  }
}
