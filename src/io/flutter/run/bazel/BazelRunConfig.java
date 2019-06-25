/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazel;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.RunConfigurationWithSuppressedDefaultRunAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.bazel.Workspace;
import io.flutter.run.FlutterDevice;
import io.flutter.run.LaunchState;
import io.flutter.run.common.RunMode;
import io.flutter.run.daemon.FlutterApp;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BazelRunConfig extends RunConfigurationBase
  implements RunConfigurationWithSuppressedDefaultRunAction, LaunchState.RunConfig {
  @NotNull private BazelFields fields;

  BazelRunConfig(final @NotNull Project project, final @NotNull ConfigurationFactory factory, @NotNull final String name) {
    super(project, factory, name);
    fields = new BazelFields(null, null, null, false);
  }

  @NotNull
  BazelFields getFields() {
    return fields;
  }

  void setFields(@NotNull BazelFields newFields) {
    fields = newFields;
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    fields.checkRunnable(getProject());
  }

  @NotNull
  @Override
  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new FlutterBazelConfigurationEditorForm(getProject());
  }

  @NotNull
  @Override
  public LaunchState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    final BazelFields launchFields = fields.copy();
    try {
      launchFields.checkRunnable(env.getProject());
    }
    catch (RuntimeConfigurationError e) {
      throw new ExecutionException(e);
    }

    final Workspace workspace = fields.getWorkspace(getProject());
    final VirtualFile workspaceRoot = workspace.getRoot();
    final RunMode mode = RunMode.fromEnv(env);
    final Module module = ModuleUtil.findModuleForFile(workspaceRoot, env.getProject());

    final LaunchState.CreateAppCallback createAppCallback = (@Nullable FlutterDevice device) -> {
      if (device == null) return null;

      final GeneralCommandLine command = getCommand(env, device);
      return FlutterApp.start(env, env.getProject(), module, mode, device, command,
                              StringUtil.capitalize(mode.mode()) + "BazelApp", "StopBazelApp");
    };

    return new LaunchState(env, workspaceRoot, workspaceRoot, this, createAppCallback);
  }

  @NotNull
  @Override
  public GeneralCommandLine getCommand(ExecutionEnvironment env, FlutterDevice device) throws ExecutionException {
    final BazelFields launchFields = fields.copy();
    final RunMode mode = RunMode.fromEnv(env);
    return launchFields.getLaunchCommand(env.getProject(), device, mode);
  }

  public BazelRunConfig clone() {
    final BazelRunConfig clone = (BazelRunConfig)super.clone();
    clone.fields = fields.copy();
    return clone;
  }

  RunConfiguration copyTemplateToNonTemplate(String name) {
    final BazelRunConfig copy = (BazelRunConfig)super.clone();
    copy.setName(name);
    copy.fields = fields.copy();
    return copy;
  }

  @Override
  public void writeExternal(@NotNull final Element element) throws WriteExternalException {
    super.writeExternal(element);
    fields.writeTo(element);
  }

  @Override
  public void readExternal(@NotNull final Element element) throws InvalidDataException {
    super.readExternal(element);
    fields = BazelFields.readFrom(element);
  }
}
