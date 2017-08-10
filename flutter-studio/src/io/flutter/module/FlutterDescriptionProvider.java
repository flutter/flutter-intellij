/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.android.tools.idea.npw.module.ModuleDescriptionProvider;
import com.android.tools.idea.npw.module.ModuleGalleryEntry;
import com.android.tools.idea.npw.module.NewModuleModel;
import com.android.tools.idea.wizard.model.SkippableWizardStep;
import com.intellij.openapi.diagnostic.Logger;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;

public class FlutterDescriptionProvider implements ModuleDescriptionProvider {
  private static final Logger LOG = Logger.getInstance(FlutterDescriptionProvider.class.getName());

  @Override
  public Collection<? extends ModuleGalleryEntry> getDescriptions() {
    ArrayList<ModuleGalleryEntry> res = new ArrayList<>();
    res.add(new FlutterPackageGalleryEntry());
    // TODO(messick): Add Flutter plugin entry.
    return res;
  }

  private static class FlutterPackageGalleryEntry implements ModuleGalleryEntry {

    @Nullable
    @Override
    public Icon getIcon() {
      return FlutterIcons.AndroidStudioNewModule;
    }

    @NotNull
    @Override
    public String getName() {
      return FlutterBundle.message("module.wizard.package_title");
    }

    @Nullable
    @Override
    public String getDescription() {
      return FlutterBundle.message("module.wizard.package_description");
    }

    @NotNull
    @Override
    public SkippableWizardStep createStep(@NotNull NewModuleModel model) {
      return new FlutterPackageStep(
        new FlutterModuleModel(model.getProject().getValue()),
        FlutterBundle.message("module.wizard.package_step_title"),
        FlutterIcons.Flutter_64);
    }
  }
}
