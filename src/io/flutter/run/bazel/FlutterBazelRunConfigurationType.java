/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run.bazel;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeBase;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.lang.dart.DartFileType;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import org.jetbrains.annotations.NotNull;

public class FlutterBazelRunConfigurationType extends ConfigurationTypeBase {

  public FlutterBazelRunConfigurationType() {
    super("FlutterBazelRunConfigurationType", FlutterBundle.message("runner.flutter.bazel.configuration.name"),
          FlutterBundle.message("runner.flutter.bazel.configuration.description"), FlutterIcons.BazelRun);
    addFactory(new FlutterBazelRunConfigurationType.FlutterBazelConfigurationFactory(this));
  }

  public static FlutterBazelRunConfigurationType getInstance() {
    return Extensions.findExtension(CONFIGURATION_TYPE_EP, FlutterBazelRunConfigurationType.class);
  }

  public static class FlutterBazelConfigurationFactory extends ConfigurationFactory {
    public FlutterBazelConfigurationFactory(FlutterBazelRunConfigurationType type) {
      super(type);
    }

    @Override
    @NotNull
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new FlutterBazelRunConfiguration(project, this, "Flutter (Bazel)");
    }

    @Override
    @NotNull
    public RunConfiguration createConfiguration(String name, RunConfiguration template) {
      // Override the default name which is always "Unnamed".
      return super.createConfiguration(template.getProject().getName(), template);
    }

    @Override
    public boolean isApplicable(@NotNull Project project) {
      return FileTypeIndex.containsFileOfType(DartFileType.INSTANCE, GlobalSearchScope.projectScope(project));
    }
  }
}
