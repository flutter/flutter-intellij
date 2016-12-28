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

  private static final String DART_GROUP_NAME = "Static Web";

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
      createProjectFiles(model, baseDir, getFlutterSdk());
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

  @Override
  public String getParentGroup() {
    return DART_GROUP_NAME;
  }

  @Override
  public ModuleType getModuleType() {
    return FlutterModuleType.getInstance();
  }

  private static void createProjectFiles(@NotNull final ModifiableRootModel model,
                                         @NotNull final VirtualFile baseDir,
                                         @NotNull final FlutterSdk sdk) {
    // Create files.
    try {
      sdk.run(FlutterSdk.Command.CREATE, model.getModule(), baseDir, null, baseDir.getPath());
    }
    catch (ExecutionException e) {
      LOG.warn(e);
    }
  }

  static FlutterSdk getFlutterSdk() {
    return FlutterSdk.getGlobalFlutterSdk();
  }

  public static void setupProject(@NotNull Project project, ModifiableRootModel model, VirtualFile baseDir, String flutterSdkPath)
    throws ConfigurationException {
    // TODO(devoncarew): Store the flutterSdkPath info (in the project? module?).
    final FlutterSdk sdk = FlutterSdk.forPath(flutterSdkPath);
    if (sdk == null) {
      throw new ConfigurationException(flutterSdkPath + " is not a valid Flutter SDK");
    }

    model.addContentEntry(baseDir);
    createProjectFiles(model, baseDir, sdk);
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
