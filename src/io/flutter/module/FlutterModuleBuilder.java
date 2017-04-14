/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.google.common.collect.ImmutableList;
import com.intellij.execution.ExecutionException;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.*;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.dart.DartPlugin;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class FlutterModuleBuilder extends ModuleBuilder {
  private static final Logger LOG = Logger.getInstance(FlutterModuleBuilder.class);

  private static final String DART_GROUP_NAME = "Static Web";

  private FlutterModuleWizardStep myStep;

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
    if (baseDir == null) {
      return;
    }
    createProjectFiles(model, baseDir, myStep.getFlutterSdk());
  }

  @Nullable
  @Override
  public List<Module> commit(@NotNull Project project, @Nullable ModifiableModuleModel model, ModulesProvider modulesProvider) {
    final List<Module> result = super.commit(project, model, modulesProvider);
    if (result == null || result.size() != 1) {
      return result;
    }

    final String baseDirPath = new File(result.get(0).getModuleFilePath()).getParent();
    final Module android = addAndroidModule(project, model, baseDirPath);
    if (android != null) {
      return ImmutableList.of(result.get(0), android);
    } else {
      return result;
    }
  }

  @Nullable
  private Module addAndroidModule(@NotNull Project project,
                                  @Nullable ModifiableModuleModel model,
                                  @NotNull String baseDirPath) {
    final VirtualFile baseDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(baseDirPath);
    if (baseDir == null) {
      return null;
    }

    final String androidPath = baseDirPath + "/android.iml";
    final VirtualFile androidFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(androidPath);
    if (androidFile == null) {
      return null;
    }

    try {
      final ModifiableModuleModel toCommit;
      if (model == null) {
        toCommit = ModuleManager.getInstance(project).getModifiableModel();
        model = toCommit;
      } else {
        toCommit = null;
      }

      final Module androidModule = model.loadModule(androidFile.getPath());

      if (toCommit != null) {
        WriteAction.run(toCommit::commit);
      }

      return androidModule;
    }
    catch (ModuleWithNameAlreadyExists exists) {
      return null;
    }
    catch (IOException e) {
      LOG.warn(e);
      return null;
    }
  }

  @Override
  public boolean validate(Project current, Project dest) {
    return myStep.getFlutterSdk() != null;
  }

  @Nullable
  @Override
  public ModuleWizardStep getCustomOptionsStep(final WizardContext context, final Disposable parentDisposable) {
    myStep = new FlutterModuleWizardStep();
    Disposer.register(parentDisposable, myStep);
    return myStep;
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
                                         @NotNull final FlutterSdk sdk) throws ConfigurationException {

    runFlutterCreate(sdk, model.getModule(), baseDir);

    try {
      DartPlugin.ensureDartSdkConfigured(model.getProject(), sdk.getDartSdkPath());
      FlutterSdkUtil.updateKnownSdkPaths(sdk.getHomePath());
    }
    catch (ExecutionException e) {
      LOG.warn("Error configuring the Flutter SDK.", e);
      throw new ConfigurationException("Error configuring Flutter SDK");
    }
  }

  private static void runFlutterCreate(@NotNull FlutterSdk sdk, Module module, @NotNull VirtualFile baseDir)
    throws ConfigurationException {
    // Create files.
    try {
      final Process process =
        sdk.startProcess(FlutterSdk.Command.CREATE, module, baseDir, null, baseDir.getPath());
      if (process != null) {
        // Wait for process to finish. (We overwrite some files, so make sure we lose the race.)
        // TODO(skybrian) the user sees a short pause. Show progress somehow?
        final int exitCode = process.waitFor();
        if (exitCode == 0) {
          baseDir.refresh(false, true);
          return; // success
        }
      }
    }
    catch (ExecutionException | InterruptedException e) {
      LOG.warn(e);
    }
    throw new ConfigurationException("flutter create command was unsuccessful");
  }

  /**
   * Set up a "small IDE" project. (For example, WebStorm.)
   */
  public static void setupSmallProject(@NotNull Project project, ModifiableRootModel model, VirtualFile baseDir, String flutterSdkPath)
    throws ConfigurationException {
    final FlutterSdk sdk = FlutterSdk.forPath(flutterSdkPath);
    if (sdk == null) {
      throw new ConfigurationException(flutterSdkPath + " is not a valid Flutter SDK");
    }
    model.addContentEntry(baseDir);
    createProjectFiles(model, baseDir, sdk);
  }

  private static class FlutterModuleWizardStep extends ModuleWizardStep implements Disposable {
    private final FlutterGeneratorPeer peer;
    private FlutterSdk myFlutterSdk;

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

    public FlutterSdk getFlutterSdk() {
      return FlutterSdk.forPath(peer.getSdkComboPath());
    }
  }
}
