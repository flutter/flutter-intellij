/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.flutter.project;

import com.android.tools.idea.projectsystem.*;
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem;
import com.intellij.openapi.project.Project;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;

public class FlutterProjectSystemProvider implements AndroidProjectSystemProvider {
  final private Project myProject;

  public FlutterProjectSystemProvider(Project project) {
    myProject = project;
  }

  @Override
  public boolean isApplicable() {
    return FlutterModuleUtils.hasFlutterModule(myProject);
  }

  @NotNull
  @Override
  public String getId() {
    return "flutter-project";
  }

  @NotNull
  @Override
  public AndroidProjectSystem getProjectSystem() {
    return new GradleProjectSystem(myProject);
  }
}
