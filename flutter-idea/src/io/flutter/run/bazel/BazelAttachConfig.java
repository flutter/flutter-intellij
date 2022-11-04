/*
 * Copyright 2022 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazel;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import io.flutter.run.FlutterDevice;
import io.flutter.run.common.RunMode;
import org.jetbrains.annotations.NotNull;

public class BazelAttachConfig extends BazelRunConfig {
  public BazelAttachConfig(@NotNull Project project,
                           @NotNull ConfigurationFactory factory,
                           @NotNull String name) {
    super(project, factory, name);
  }

  public BazelAttachConfig(BazelRunConfig configuration) {
    super(configuration.getProject(), configuration.getFactory(), configuration.getName());
    setFields(configuration.fields);
  }

  @NotNull
  @Override
  public GeneralCommandLine getCommand(ExecutionEnvironment env, @NotNull FlutterDevice device) throws ExecutionException {
    final BazelFields launchFields = fields.copy();
    final RunMode mode = RunMode.fromEnv(env);
    return launchFields.getLaunchCommand(env.getProject(), device, mode, true);
  }

}
