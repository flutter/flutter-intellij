/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.test;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import icons.FlutterIcons;
import io.flutter.run.FlutterRunConfigurationType;
import org.jetbrains.annotations.NotNull;

/**
 * The type of configs that run tests using "flutter test".
 */
public class TestConfigType extends ConfigurationTypeBase {
  protected TestConfigType() {
    super("FlutterTestConfigType", "Flutter Test",
          "description", FlutterIcons.Flutter_test);
    addFactory(new Factory(this));
  }

  public static TestConfigType getInstance() {
    return Extensions.findExtension(CONFIGURATION_TYPE_EP, TestConfigType.class);
  }

  private static class Factory extends ConfigurationFactory {
    public Factory(TestConfigType type) {
      super(type);
    }

    @NotNull
    @Override
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new TestConfig(project, this, "Flutter Test");
    }

    @Override
    public boolean isApplicable(@NotNull Project project) {
      return FlutterRunConfigurationType.doShowFlutterRunConfigurationForProject(project);
    }
  }
}
