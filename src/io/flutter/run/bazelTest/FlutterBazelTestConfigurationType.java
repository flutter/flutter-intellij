/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.*;
import com.intellij.openapi.components.BaseState;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.run.bazel.FlutterBazelRunConfigurationType;
import io.flutter.run.test.FlutterTestConfigType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The Bazel version of the {@link FlutterTestConfigType} configuration.
 */
public class FlutterBazelTestConfigurationType extends ConfigurationTypeBase {

  final ConfigurationFactory factory = new Factory(this);
  final ConfigurationFactory watchFactory = new WatchFactory(this);

  protected FlutterBazelTestConfigurationType() {
    super("FlutterBazelTestConfigurationType", FlutterBundle.message("runner.flutter.bazel.test.configuration.name"),
          FlutterBundle.message("runner.flutter.bazel.configuration.description"), FlutterIcons.BazelRun);
    // TODO: How to make multiple factories work with the run button in the left-hand tray?
    addFactory(watchFactory);
    addFactory(factory);

  }

  public static FlutterBazelTestConfigurationType getInstance() {
    return Extensions.findExtension(CONFIGURATION_TYPE_EP, FlutterBazelTestConfigurationType.class);
  }

  private static class Options extends BaseState {
  }

  static class Factory extends ConfigurationFactory {
    public Factory(FlutterBazelTestConfigurationType type) {
      super(type);
    }

    @Override
    public @Nullable Class<? extends BaseState> getOptionsClass() {
      return super.getOptionsClass();
    }

    @Override
    @NotNull
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      // This is always called first when loading a run config, even when it's a non-template config.
      // See RunManagerImpl.doCreateConfiguration
      return new BazelTestConfig(project, this, FlutterBundle.message("runner.flutter.bazel.test.configuration.name"));
    }

    @Override
    public boolean isApplicable(@NotNull Project project) {
      return FlutterBazelRunConfigurationType.doShowBazelRunConfigurationForProject(project);
    }

    @Override
    public @NotNull String getId() {
      return "No Watch";
    }
  }

  static class WatchFactory extends ConfigurationFactory {

    public WatchFactory(FlutterBazelTestConfigurationType type) {
      super(type);
    }

    @Override
    @NotNull
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      // This is always called first when loading a run config, even when it's a non-template config.
      // See RunManagerImpl.doCreateConfiguration
      BazelTestConfig config = new BazelTestConfig(project, this, FlutterBundle.message("runner.flutter.bazel.test.configuration.name"));
      config.setName("Watch " + config.getName());
      config.setFields(new BazelTestFields(null, null, null, "--watch"));
      return config;
    }

    @Override
    public @NotNull String getName() {
      return "Watch " + super.getName();
    }

    @Override
    public @NotNull String getId() {
      return "Watch";
    }
  }
}
