/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.google.common.base.Joiner;
import com.intellij.execution.Executor;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindowId;
import io.flutter.FlutterInitializer;
import io.flutter.FlutterUtils;
import io.flutter.run.FlutterLaunchMode;
import io.flutter.run.SdkFields;
import io.flutter.run.SdkRunConfig;
import io.flutter.run.attach.SdkAttachConfig;
import org.jetbrains.android.actions.AndroidConnectDebuggerAction;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class ConnectAndroidDebuggerAction extends AndroidConnectDebuggerAction {
  @SuppressWarnings("FieldCanBeLocal")
  private static String RUN_DEBUG_LINK = "https://flutter.io/using-ide/#running-and-debugging";

  @Override
  public void actionPerformed(AnActionEvent e) {
    // NOTE: When making changes here, consider making similar changes to RunFlutterAction.
    if (!FlutterUtils.isAndroidStudio()) {
      super.actionPerformed(e);
      return;
    }
    FlutterInitializer.sendAnalyticsAction(this);

    RunnerAndConfigurationSettings settings = RunFlutterAction.getRunConfigSettings(e);
    if (settings == null) {
      showSelectConfigDialog();
      return;
    }

    RunConfiguration configuration = settings.getConfiguration();
    if (!(configuration instanceof SdkRunConfig)) {
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

    ExecutionEnvironment env = builder.activeTarget().dataContext(e.getDataContext()).build();
    FlutterLaunchMode.addToEnvironment(env, FlutterLaunchMode.DEBUG);

    ProgramRunnerUtil.executeConfiguration(env, false, true);
  }

  @Override
  public void update(AnActionEvent e) {
    // TODO(messick): Remove this method if there is no special update requirement.
    if (!FlutterUtils.isAndroidStudio()) {
      super.update(e);
      return;
    }
    super.update(e);
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
                            RUN_DEBUG_LINK +
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
