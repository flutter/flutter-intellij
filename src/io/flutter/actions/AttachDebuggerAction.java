/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.execution.*;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.util.ThreeState;
import com.intellij.util.messages.MessageBusConnection;
import io.flutter.FlutterConstants;
import io.flutter.bazel.Workspace;
import io.flutter.pub.PubRoot;
import io.flutter.run.FlutterLaunchMode;
import io.flutter.run.SdkAttachConfig;
import io.flutter.run.SdkRunConfig;
import io.flutter.run.bazel.BazelAttachConfig;
import io.flutter.run.bazel.BazelRunConfig;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import io.flutter.utils.OpenApiUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.function.Consumer;

public class AttachDebuggerAction extends FlutterSdkAction {

  // Is 'flutter attach' running for a project?
  // The three states are true, false, and non-existent (null).
  //   true = run automatically via the debug listener in FlutterStudioStartupActivity
  //   false = run from button press here
  //   null = no attach process is running
  // The button is still required because there may be multiple Flutter run configs.
  public static final Key<ThreeState> ATTACH_IS_ACTIVE = new Key<>("attach-is-active");

  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void startCommand(@NotNull Project project, @NotNull FlutterSdk sdk, @Nullable PubRoot root, @NotNull DataContext context) {
    // NOTE: When making changes here, consider making similar changes to RunFlutterAction.
    RunConfiguration configuration = findRunConfig(project);
    if (configuration == null) {
      final RunnerAndConfigurationSettings settings = RunManagerEx.getInstanceEx(project).getSelectedConfiguration();
      if (settings == null) {
        showSelectConfigDialog();
        return;
      }
      configuration = settings.getConfiguration();
      if (!(configuration instanceof SdkRunConfig)) {
        if (project.isDefault() || !FlutterSdkUtil.hasFlutterModules(project)) {
          return;
        }
        showSelectConfigDialog();
        return;
      }
    }

    final SdkAttachConfig sdkRunConfig = new SdkAttachConfig((SdkRunConfig)configuration);
    sdkRunConfig.pubRoot = root;
    final Executor executor = RunFlutterAction.getExecutor(ToolWindowId.DEBUG);
    if (executor == null) {
      return;
    }

    final ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.create(executor, sdkRunConfig);

    final ExecutionEnvironment env = builder.activeTarget().dataContext(context).build();
    FlutterLaunchMode.addToEnvironment(env, FlutterLaunchMode.DEBUG);

    if (project.getUserData(ATTACH_IS_ACTIVE) == null) {
      project.putUserData(ATTACH_IS_ACTIVE, ThreeState.fromBoolean(false));
      onAttachTermination(project, (p) -> p.putUserData(ATTACH_IS_ACTIVE, null));
    }
    ProgramRunnerUtil.executeConfiguration(env, false, true);
  }

  public void startCommandInBazelContext(@NotNull Project project, @NotNull Workspace workspace, @NotNull AnActionEvent event) {
    RunConfiguration configuration = findRunConfig(project);
    if (configuration == null) {
      final RunnerAndConfigurationSettings settings = RunManagerEx.getInstanceEx(project).getSelectedConfiguration();
      if (settings == null) {
        showSelectConfigDialog();
        return;
      }
      configuration = settings.getConfiguration();
      if (!(configuration instanceof BazelRunConfig)) {
        return;
      }
    }

    final BazelAttachConfig sdkRunConfig = new BazelAttachConfig((BazelRunConfig)configuration);
    final Executor executor = RunFlutterAction.getExecutor(ToolWindowId.DEBUG);
    if (executor == null) {
      return;
    }

    final ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.create(executor, sdkRunConfig);

    final ExecutionEnvironment env = builder.activeTarget().dataContext(event.getDataContext()).build();
    FlutterLaunchMode.addToEnvironment(env, FlutterLaunchMode.DEBUG);

    ProgramRunnerUtil.executeConfiguration(env, false, true);
  }

  public boolean enableActionInBazelContext() {
    return true;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null || project.isDefault()) {
      super.update(e);
      return;
    }
    if (!FlutterSdkUtil.hasFlutterModules(project)) {
      // Hide this button in Android projects.
      e.getPresentation().setVisible(false);
      return;
    }
    RunConfiguration configuration = findRunConfig(project);
    boolean enabled;
    if (configuration == null) {
      final RunnerAndConfigurationSettings settings = RunManagerEx.getInstanceEx(project).getSelectedConfiguration();
      if (settings == null) {
        enabled = false;
      }
      else {
        configuration = settings.getConfiguration();
        enabled = configuration instanceof SdkRunConfig || configuration instanceof BazelRunConfig;
      }
    }
    else {
      enabled = true;
    }
    if (enabled && (project.getUserData(ATTACH_IS_ACTIVE) != null)) {
      enabled = false;
    }
    e.getPresentation().setVisible(true);
    e.getPresentation().setEnabled(enabled);
  }

  @Nullable
  public static RunConfiguration findRunConfig(@NotNull Project project) {
    // Look for a Flutter run config. If exactly one is found then return it otherwise return null.
    final RunManagerEx mgr = RunManagerEx.getInstanceEx(project);
    final List<RunConfiguration> configs = mgr.getAllConfigurationsList();
    int count = 0;
    RunConfiguration sdkConfig = null;
    for (RunConfiguration config : configs) {
      if (config instanceof SdkRunConfig) {
        count += 1;
        sdkConfig = config;
      }
    }
    return count == 1 ? sdkConfig : null;
  }

  private static void onAttachTermination(@NotNull Project project, @NotNull Consumer<@NotNull Project> runner) {
    final MessageBusConnection connection = project.getMessageBus().connect();

    // Need an ExecutionListener to clean up project-scoped state when the Stop button is clicked.
    connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
      Object handler;

      @Override
      public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
        if (env.getRunProfile() instanceof SdkAttachConfig) {
          this.handler = handler;
        }
      }

      @Override
      public void processTerminated(@NotNull String executorId,
                                    @NotNull ExecutionEnvironment env,
                                    @NotNull ProcessHandler handler,
                                    int exitCode) {
        if (this.handler == handler) {
          runner.accept(project);
          connection.disconnect();
        }
      }
    });
  }

  private static void showSelectConfigDialog() {
    OpenApiUtils.safeInvokeLater(() -> new SelectConfigDialog().show(), ModalityState.nonModal());
  }

  private static class SelectConfigDialog extends DialogWrapper {
    private JPanel myPanel;
    private JTextPane myTextPane;

    SelectConfigDialog() {
      super(null, false, false);
      setTitle("Run Configuration");
      myPanel = new JPanel();
      myTextPane = new JTextPane();
      Messages.installHyperlinkSupport(myTextPane);
      final String selectConfig = "<html><body>" +
                                  "<p>The run configuration for the Flutter module must be selected." +
                                  "<p>Please change the run configuration to the one created when the<br>" +
                                  "module was created. See <a href=\"" +
                                  FlutterConstants.URL_RUN_AND_DEBUG +
                                  "\">the Flutter documentation</a> for more information.</body></html>";
      //noinspection DataFlowIssue
      myTextPane.setText(selectConfig);
      //noinspection DataFlowIssue
      myPanel.add(myTextPane);
      init();
      getButton(getCancelAction()).setVisible(false);
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      return myPanel;
    }
  }
}
