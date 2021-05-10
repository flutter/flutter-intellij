/*
 * Copyright 2021 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.coverage;

import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageExecutor;
import com.intellij.coverage.CoverageRunnerData;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.ConfigurationInfoProvider;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.runners.DefaultProgramRunnerKt;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import io.flutter.FlutterBundle;
import io.flutter.run.test.TestConfig;
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

  @Override
  @Nullable
  protected RunContentDescriptor doExecute(final @NotNull RunProfileState state,
                                           final @NotNull ExecutionEnvironment env) throws ExecutionException {
    final RunContentDescriptor result = DefaultProgramRunnerKt.executeState(state, env, this);
    if (result == null) {
      return null;
    }
    if (result.getProcessHandler() != null) {
      result.getProcessHandler().addProcessListener(new ProcessAdapter() {
        public void processTerminated(@NotNull ProcessEvent event) {
          ApplicationManager.getApplication().invokeLater(() -> processCoverage(env));
        }
      });
    }
    return result;
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
