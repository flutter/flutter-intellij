/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

//import com.android.tools.idea.projectsystem.*;
//import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem;
import com.intellij.openapi.project.Project;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;

// This is needed in 3.1 builds but not 3.0.
public class FlutterProjectSystemProvider /*implements AndroidProjectSystemProvider*/ {
  final private Project myProject;

  public FlutterProjectSystemProvider(Project project) {
    myProject = project;
  }

  //@Override
  public boolean isApplicable() {
    return FlutterModuleUtils.hasFlutterModule(myProject);
  }

  @NotNull
  //@Override
  public String getId() {
    return "flutter-project";
  }

  //@NotNull
  //@Override
  //public AndroidProjectSystem getProjectSystem() {
  //  return new GradleProjectSystem(myProject);
  //}
}
