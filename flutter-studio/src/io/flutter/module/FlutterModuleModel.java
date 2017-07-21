/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.android.tools.idea.ui.properties.core.StringProperty;
import com.android.tools.idea.ui.properties.core.StringValueProperty;
import com.android.tools.idea.wizard.model.WizardModel;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class FlutterModuleModel extends WizardModel {
  @NotNull private final StringProperty myPackageName = new StringValueProperty();
  @NotNull private final StringProperty myContentRoot = new StringValueProperty();
  @NotNull private final StringProperty myPackageFileLocation = new StringValueProperty();
  @NotNull private final StringValueProperty myFlutterSdk = new StringValueProperty();
  @NotNull private final Project myProject;

  public FlutterModuleModel(@NotNull Project project) {
    myProject = project;
    myPackageName.addConstraint(String::trim);
    myContentRoot.addConstraint(String::trim);
    myPackageFileLocation.addConstraint(String::trim);
    myFlutterSdk.addConstraint(String::trim);
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public StringProperty packageName() {
    return myPackageName;
  }

  @NotNull
  public StringProperty contentRoot() {
    return myContentRoot;
  }

  @NotNull
  public StringProperty packageFileLocation() {
    return myPackageFileLocation;
  }

  @NotNull
  public StringValueProperty flutterSdk() {
    return myFlutterSdk;
  }

  @Override
  protected void handleFinished() {
    // TODO Fork 'flutter create'.
  }
}
