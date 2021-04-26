/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.coverage;

import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageExecutor;
import com.intellij.coverage.CoverageRunnerData;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.ConfigurationInfoProvider;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.runners.DefaultProgramRunnerKt;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ReflectionUtil;
import io.flutter.FlutterBundle;
import io.flutter.run.test.TestConfig;
import io.flutter.run.test.TestFields;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FlutterCoverageProgramRunner extends GenericProgramRunner<RunnerSettings> {
  private static final Logger LOG = Logger.getInstance(FlutterCoverageProgramRunner.class.getName());

  private static final String ID = "FlutterCoverageProgramRunner";

  @Override
  public @NotNull
  @NonNls
  String getRunnerId() {
    return ID;
  }

  @Override
  public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
    return executorId.equals(CoverageExecutor.EXECUTOR_ID) && profile instanceof TestConfig;
  }

  @Override
  public RunnerSettings createConfigurationData(@NotNull final ConfigurationInfoProvider settingsProvider) {
    return new CoverageRunnerData();
  }

  @Nullable
  @Override
  protected RunContentDescriptor doExecute(final @NotNull RunProfileState state,
                                           final @NotNull ExecutionEnvironment env) throws ExecutionException {
    final ExecutionEnvironment covEnv = copyEnvironmentWithCoverageParam(env);
    if (covEnv == null) {
      return null;
    }
    //final RunProfileState newState = covEnv.getRunProfile().getState(env.getExecutor(), covEnv);
    //assert newState != null;
    final RunContentDescriptor result = DefaultProgramRunnerKt.executeState(state, covEnv, this);
    if (result == null) {
      return null;
    }
    if (result.getProcessHandler() != null) {
      result.getProcessHandler().addProcessListener(new ProcessAdapter() {
        public void processTerminated(@NotNull ProcessEvent event) {
          ApplicationManager.getApplication().invokeLater(() -> processCoverage(covEnv));
        }
      });
    }
    return result;
  }

  @Nullable
  private static ExecutionEnvironment copyEnvironmentWithCoverageParam(@NotNull ExecutionEnvironment env) {
    if (env.getRunnerAndConfigurationSettings() == null) return env;
    final RunProfile profile = env.getRunProfile();
    if (!(profile instanceof TestConfig)) return null;
    final TestConfig config = (TestConfig)profile;
    final TestFields fields = config.getFields();

    @Nullable String args = fields.getAdditionalArgs();
    if (args == null) {
      args = "--coverage";
    }
    else if (!args.contains("--coverage")) {
      args += " --coverage";
    }
    else {
      return env;
    }
    final TestFields newFields = fields.copy();
    newFields.setAdditionalArgs(args);

    final ExecutionEnvironment newEnv =
      new ExecutionEnvironment(new DefaultRunExecutor(), env.getRunner(), env.getRunnerAndConfigurationSettings(), env.getProject());
    final TestConfig oldConfig = (TestConfig)newEnv.getRunProfile();
    final TestConfig newConfig = (TestConfig)oldConfig.clone();
    newConfig.setFields(newFields);
    ReflectionUtil.setField(ExecutionEnvironment.class, newEnv, RunProfile.class, "myRunProfile", newConfig);
    return newEnv;
  }

  private static void processCoverage(ExecutionEnvironment env) {
    if (!(env.getRunProfile() instanceof TestConfig)) return;
    final TestConfig runConfig = (TestConfig)env.getRunProfile();
    final CoverageEnabledConfiguration configuration = CoverageEnabledConfiguration.getOrCreate(runConfig);
    if (configuration.getCoverageFilePath() == null) return;

    final Path path = Paths.get(configuration.getCoverageFilePath());
    if (Files.exists(path)) {
      @Nullable final RunnerSettings settings = env.getRunnerSettings();
      if (settings != null) {
        CoverageDataManager.getInstance(env.getProject()).processGatheredCoverage(runConfig, settings);
      }
    }
    else {
      LOG.error(FlutterBundle.message("coverage.path.not.found", path));
    }
  }
}
