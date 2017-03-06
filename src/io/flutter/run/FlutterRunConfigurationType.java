/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

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
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;

public class FlutterRunConfigurationType extends ConfigurationTypeBase {

  public FlutterRunConfigurationType() {
    super("FlutterRunConfigurationType", FlutterBundle.message("runner.flutter.configuration.name"),
          FlutterBundle.message("runner.flutter.configuration.description"), FlutterIcons.Flutter);
    addFactory(new FlutterConfigurationFactory(this));
  }

  public static FlutterRunConfigurationType getInstance() {
    return Extensions.findExtension(CONFIGURATION_TYPE_EP, FlutterRunConfigurationType.class);
  }

  public static class FlutterConfigurationFactory extends ConfigurationFactory {
    public FlutterConfigurationFactory(FlutterRunConfigurationType type) {
      super(type);
    }

    @Override
    @NotNull
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
      return new SdkRunConfig(project, this, "Flutter");
    }

    @Override
    @NotNull
    public RunConfiguration createConfiguration(String name, RunConfiguration template) {
      // Override the default name which is always "Unnamed".
      return super.createConfiguration(template.getProject().getName(), template);
    }

    @Override
    public boolean isApplicable(@NotNull Project project) {
      return FileTypeIndex.containsFileOfType(DartFileType.INSTANCE, GlobalSearchScope.projectScope(project)) &&
             FlutterModuleUtils.hasFlutterModule(project);
    }
  }
}
