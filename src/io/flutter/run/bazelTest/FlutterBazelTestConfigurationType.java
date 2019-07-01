/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazelTest;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.run.bazel.FlutterBazelRunConfigurationType;
import io.flutter.run.test.FlutterTestConfigType;
import org.jetbrains.annotations.NotNull;

/**
 * The Bazel version of the {@link FlutterTestConfigType} configuration.
 */
public class FlutterBazelTestConfigurationType extends ConfigurationTypeBase {

  final ConfigurationFactory factory = new Factory(this);
  final ConfigurationFactory watchFactory = new WatchFactory(this);

  protected FlutterBazelTestConfigurationType() {
    super("FlutterBazelTestConfigurationType", FlutterBundle.message("runner.flutter.bazel.test.configuration.name"),
          FlutterBundle.message("runner.flutter.bazel.configuration.description"), FlutterIcons.BazelRun);
    // Note that for both factories to produce inline run configurations for the left-hand tray context menu,
    // the Registry flag `suggest.all.run.configurations.from.context` should be enabled.
    // Otherwise, only one configuration may show up.
    addFactory(factory);
    addFactory(watchFactory);

  }

  public static FlutterBazelTestConfigurationType getInstance() {
    return Extensions.findExtension(CONFIGURATION_TYPE_EP, FlutterBazelTestConfigurationType.class);
  }

  /**
   * {@link ConfigurationFactory} for Flutter Bazel tests that run one-off.
   */
  static class Factory extends ConfigurationFactory {
    private Factory(FlutterBazelTestConfigurationType type) {
      super(type);
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
    @NotNull
    public String getId() {
      return FlutterBundle.message("runner.flutter.bazel.test.configuration.name");
    }
  }

  /**
   * {@link ConfigurationFactory} for Flutter Bazel tests that watch test results and re-run them.
   */
  static class WatchFactory extends ConfigurationFactory {

    private WatchFactory(FlutterBazelTestConfigurationType type) {
      super(type);
    }

    @Override
    @NotNull
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      // This is always called first when loading a run config, even when it's a non-template config.
      // See RunManagerImpl.doCreateConfiguration
      BazelTestConfig config = new BazelTestConfig(
        project, this, FlutterBundle.message("runner.flutter.bazel.watch.test.configuration.name"));
      config.setFields(new BazelTestFields(null, null, null, "--watch"));
      return config;
    }

    @Override
    public boolean isApplicable(@NotNull Project project) {
      return FlutterBazelRunConfigurationType.doShowBazelRunConfigurationForProject(project);
    }

    @Override
    public @NotNull String getName() {
      return "Watch " + super.getName();
    }

    @Override
    public @NotNull String getId() {
      return FlutterBundle.message("runner.flutter.bazel.watch.test.configuration.name");
    }
  }
}
