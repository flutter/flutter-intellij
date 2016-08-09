/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import org.jetbrains.annotations.NotNull;
import io.flutter.FlutterBundle;

public class FlutterRunConfigurationType extends ConfigurationTypeBase {

  public FlutterRunConfigurationType() {
    super("FlutterRunConfigurationType", FlutterBundle.message("runner.flutter.configuration.name"),
          FlutterBundle.message("runner.flutter.configuration.description"), FlutterIcons.Flutter_16);
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
      return new FlutterRunConfiguration(project, this, "Flutter");
    }

    @Override
    public boolean isApplicable(@NotNull Project project) {
      return FileTypeIndex.containsFileOfType(DartFileType.INSTANCE, GlobalSearchScope.projectScope(project));
    }
  }
}
