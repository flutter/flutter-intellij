/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.google.common.base.Joiner;
import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.util.ThreeState;
import com.intellij.util.messages.MessageBusConnection;
import io.flutter.FlutterConstants;
import io.flutter.FlutterInitializer;
import io.flutter.pub.PubRoot;
import io.flutter.run.FlutterLaunchMode;
import io.flutter.run.SdkAttachConfig;
import io.flutter.run.SdkFields;
import io.flutter.run.SdkRunConfig;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextPane;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AttachDebuggerAction extends FlutterSdkAction {

  // Is 'flutter attach' running for a project?
  // The three states are true, false, and non-existent (null).
  //   true = run automatically via the debug listener in FlutterStudioStartupActivity
  //   false = run from button press here
  //   null = no attach process is running
  // The button is still required because there may be multiple Flutter run configs.
  public static final Key<ThreeState> ATTACH_IS_ACTIVE = new Key<>("attach-is-active");

  @Override
  public void startCommand(@NotNull Project project, @NotNull FlutterSdk sdk, @Nullable PubRoot root, @NotNull DataContext context) {
    // NOTE: When making changes here, consider making similar changes to RunFlutterAction.
    FlutterInitializer.sendAnalyticsAction(this);

    RunConfiguration configuration = findRunConfig(project);
    if (configuration == null) {
      RunnerAndConfigurationSettings settings = RunManagerEx.getInstanceEx(project).getSelectedConfiguration();
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

    SdkAttachConfig sdkRunConfig = new SdkAttachConfig((SdkRunConfig)configuration);
    SdkFields fields = sdkRunConfig.getFields();
    String additionalArgs = fields.getAdditionalArgs();

    String flavorArg = null;
    if (fields.getBuildFlavor() != null) {
      flavorArg = "--flavor=" + fields.getBuildFlavor();
    }

    List<String> args = new ArrayList<>();
    if (additionalArgs != null) {
      args.add(additionalArgs);
    }
    if (flavorArg != null) {
      args.add(flavorArg);
    }
    if (!args.isEmpty()) {
      fields.setAdditionalArgs(Joiner.on(" ").join(args));
    }

    Executor executor = RunFlutterAction.getExecutor(ToolWindowId.DEBUG);
    if (executor == null) {
      return;
    }

    ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.create(executor, sdkRunConfig);

    ExecutionEnvironment env = builder.activeTarget().dataContext(context).build();
    FlutterLaunchMode.addToEnvironment(env, FlutterLaunchMode.DEBUG);

    if (project.getUserData(ATTACH_IS_ACTIVE) == null) {
      project.putUserData(ATTACH_IS_ACTIVE, ThreeState.fromBoolean(false));
      onAttachTermination(project, (p) -> p.putUserData(ATTACH_IS_ACTIVE, null));
    }
    ProgramRunnerUtil.executeConfiguration(env, false, true);
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
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
      RunnerAndConfigurationSettings settings = RunManagerEx.getInstanceEx(project).getSelectedConfiguration();
      if (settings == null) {
        enabled = false;
      }
      else {
        configuration = settings.getConfiguration();
        enabled = configuration instanceof SdkRunConfig;
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
  public static RunConfiguration findRunConfig(Project project) {
    // Look for a Flutter run config. If exactly one is found then return it otherwise return null.
    RunManagerEx mgr = RunManagerEx.getInstanceEx(project);
    List<RunConfiguration> configs = mgr.getAllConfigurationsList();
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

  private static void onAttachTermination(@NotNull Project project, @NotNull Consumer<Project> runner) {
    MessageBusConnection connection = project.getMessageBus().connect();

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
    ApplicationManager.getApplication().invokeLater(() -> new SelectConfigDialog().show(), ModalityState.NON_MODAL);
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
      String selectConfig = "<html><body>" +
                            "<p>The run configuration for the Flutter module must be selected." +
                            "<p>Please change the run configuration to the one created when the<br>" +
                            "module was created. See <a href=\"" +
                            FlutterConstants.URL_RUN_AND_DEBUG +
                            "\">the Flutter documentation</a> for more information.</body></html>";
      myTextPane.setText(selectConfig);
      myPanel.add(myTextPane);
      init();
      //noinspection ConstantConditions
      getButton(getCancelAction()).setVisible(false);
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      return myPanel;
    }
  }
}
