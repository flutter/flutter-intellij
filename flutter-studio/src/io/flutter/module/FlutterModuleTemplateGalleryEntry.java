/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.module.ModuleTemplateGalleryEntry;
import com.android.tools.idea.npw.module.NewModuleModel;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

public class FlutterModuleTemplateGalleryEntry implements ModuleTemplateGalleryEntry {
  private final File myTemplateFile;
  private final FormFactor myFormFactor;
  private final int myMinSdkLevel;
  private final boolean myIsLibrary;
  private final Icon myIcon;
  private final String myName;
  private final String myDescription;

  FlutterModuleTemplateGalleryEntry(File templateFile, FormFactor formFactor, int minSdkLevel, boolean isLibrary,
                                    Icon icon, String name, String description) {
    this.myTemplateFile = templateFile;
    this.myFormFactor = formFactor;
    this.myMinSdkLevel = minSdkLevel;
    this.myIsLibrary = isLibrary;
    this.myIcon = icon;
    this.myName = name;
    this.myDescription = description;
  }

  @NotNull
  @Override
  public File getTemplateFile() {
    return myTemplateFile;
  }

  @NotNull
  @Override
  public FormFactor getFormFactor() {
    return myFormFactor;
  }

  @Override
  public boolean isLibrary() {
    return myIsLibrary;
  }

  @Override
  public boolean isInstantApp() {
    return false;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return myIcon;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Nullable
  @Override
  public String getDescription() {
    return myDescription;
  }

  @Override
  public String toString() {
    return getName();
  }

  @NotNull
  @Override
  public SkippableWizardStep createStep(@NotNull NewModuleModel model) {
    return new ConfigureFlutterModuleStep(model, myFormFactor, myMinSdkLevel, isLibrary(), false, myName);
  }
}
