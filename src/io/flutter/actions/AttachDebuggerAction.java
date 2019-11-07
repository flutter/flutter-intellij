/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.google.common.base.Joiner;
import com.intellij.execution.Executor;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindowId;
import io.flutter.FlutterConstants;
import io.flutter.FlutterInitializer;
import io.flutter.pub.PubRoot;
import io.flutter.run.FlutterLaunchMode;
import io.flutter.run.SdkAttachConfig;
import io.flutter.run.SdkFields;
import io.flutter.run.SdkRunConfig;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class AttachDebuggerAction extends FlutterSdkAction {
  @Override
  public void startCommand(@NotNull Project project, @NotNull FlutterSdk sdk, @Nullable PubRoot root, @NotNull DataContext context) {
    // NOTE: When making changes here, consider making similar changes to RunFlutterAction.
    FlutterInitializer.sendAnalyticsAction(this);

    RunnerAndConfigurationSettings settings = RunManagerEx.getInstanceEx(project).getSelectedConfiguration();
    if (settings == null) {
      showSelectConfigDialog();
      return;
    }

    RunConfiguration configuration = settings.getConfiguration();
    if (!(configuration instanceof SdkRunConfig)) {
      if (project.isDefault() || !FlutterSdkUtil.hasFlutterModules(project)) {
        return;
      }
      showSelectConfigDialog();
      return;
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
    boolean enabled;
    RunnerAndConfigurationSettings settings = RunManagerEx.getInstanceEx(project).getSelectedConfiguration();
    if (settings == null) {
      enabled = false;
    }
    else {
      RunConfiguration configuration = settings.getConfiguration();
      enabled = configuration instanceof SdkRunConfig;
    }
    e.getPresentation().setVisible(true);
    e.getPresentation().setEnabled(enabled);
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
