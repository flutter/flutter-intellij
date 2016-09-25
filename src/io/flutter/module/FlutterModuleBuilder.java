/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.intellij.execution.ExecutionException;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FlutterModuleBuilder extends ModuleBuilder {

  private static final Logger LOG = Logger.getInstance(FlutterModuleBuilder.class);

  @Override
  public String getName() {
    return getPresentableName();
  }

  @Override
  public String getPresentableName() {
    return FlutterBundle.message("flutter.module.name");
  }

  @Override
  public Icon getBigIcon() {
    return FlutterIcons.Flutter_2x;
  }

  @Override
  public Icon getNodeIcon() {
    return FlutterIcons.Flutter;
  }

  @Override
  public void setupRootModel(ModifiableRootModel model) throws ConfigurationException {
    final ContentEntry contentEntry = doAddContentEntry(model);
    final VirtualFile baseDir = contentEntry == null ? null : contentEntry.getFile();
    if (baseDir != null) {
      setupProject(model, baseDir);
    }
  }

  @Override
  public boolean validate(Project current, Project dest) {
    return FlutterSdk.getGlobalFlutterSdk() != null;
  }

  @Nullable
  @Override
  public ModuleWizardStep getCustomOptionsStep(final WizardContext context, final Disposable parentDisposable) {
    final FlutterModuleWizardStep step = new FlutterModuleWizardStep();
    Disposer.register(parentDisposable, step);
    return step;
  }

  @SuppressWarnings("EmptyMethod")
  @Override
  public String getParentGroup() {
    //TODO(pq): find an appropriate parent group
    return super.getParentGroup();
  }

  @Override
  public ModuleType getModuleType() {
    return FlutterModuleType.getInstance();
  }


  void setupProject(@NotNull final ModifiableRootModel model,
                    @NotNull final VirtualFile baseDir) {

    final FlutterSdk sdk = getFlutterSdk();
    if (sdk == null) {
      // Validation happens in generator peer.
      return;
    }

    // Create files.
    try {
      sdk.run(FlutterSdk.Command.CREATE, model.getModule(), baseDir, null, baseDir.getPath());
    }
    catch (ExecutionException e) {
      LOG.warn(e);
    }
  }

  FlutterSdk getFlutterSdk() {
    return FlutterSdk.getGlobalFlutterSdk();
  }

  private static class FlutterModuleWizardStep extends ModuleWizardStep implements Disposable {
    private final FlutterGeneratorPeer peer;

    public FlutterModuleWizardStep() {
      this.peer = new FlutterGeneratorPeer();
    }

    @Override
    public JComponent getComponent() {
      return peer.getComponent();
    }

    @Override
    public void updateDataModel() {
    }

    @Override
    public boolean validate() throws ConfigurationException {
      final boolean valid = peer.validate();
      if (valid) {
        peer.apply();
      }
      return valid;
    }

    @Override
    public void dispose() {
    }
  }
}
