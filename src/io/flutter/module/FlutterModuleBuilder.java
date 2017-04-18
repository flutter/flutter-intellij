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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.Messages;
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
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
    doAddContentEntry(model);
    // Add a reference to Dart SDK project library, without committing.
    model.addInvalidLibrary("Dart SDK", "project");
  }

  @Nullable
  @Override
  public List<Module> commit(@NotNull Project project, @Nullable ModifiableModuleModel model, ModulesProvider modulesProvider) {
    final String basePath = getModuleFileDirectory();
    if (basePath == null) {
      Messages.showErrorDialog("Module path not set", "Internal Error");
      return null;
    }
    final VirtualFile baseDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(basePath);
    if (baseDir == null) {
      Messages.showErrorDialog("Unable to determine Flutter project directory", "Internal Error");
      return null;
    }

    final FlutterSdk sdk = myStep.getFlutterSdk();
    if (!runFlutterCreateWithProgress(baseDir, sdk, project)) {
      Messages.showErrorDialog("Flutter create command was unsuccessful", "Error");
      return null;
    }
    FlutterSdkUtil.updateKnownSdkPaths(sdk.getHomePath());

    // Create the Flutter module. This indirectly calls setupRootModule, etc.
    final List<Module> result = super.commit(project, model, modulesProvider);
    if (result == null || result.size() != 1) {
      return result;
    }
    final Module flutter = result.get(0);

    final Module android = addAndroidModule(project, model, basePath);
    if (android != null) {
      return ImmutableList.of(flutter, android);
    }
    else {
      return ImmutableList.of(flutter);
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
      }
      else {
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

  /**
   * Runs flutter create without showing a console, but with an indeterminate progress dialog.
   */
  private static boolean runFlutterCreateWithProgress(@NotNull VirtualFile baseDir,
                                                      @NotNull FlutterSdk sdk,
                                                      @NotNull Project project) {
    final ProgressManager progress = ProgressManager.getInstance();
    final AtomicBoolean succeeded = new AtomicBoolean(false);

    progress.runProcessWithProgressSynchronously(() -> {
      progress.getProgressIndicator().setIndeterminate(true);
      try {
        runFlutterCreate(sdk, baseDir, null);
        succeeded.set(true);
      }
      catch (ConfigurationException e) {
        LOG.warn(e);
      }
    }, "Creating Flutter Project", false, project);

    return succeeded.get();
  }

  /**
   * Runs flutter create. If the module parameter isn't null, shows output in a console.
   */
  private static void runFlutterCreate(@NotNull FlutterSdk sdk,
                                       @NotNull VirtualFile baseDir,
                                       @Nullable Module module)
    throws ConfigurationException {
    // Create files.
    try {
      final Process process =
        sdk.startProcess(FlutterSdk.Command.CREATE, module, baseDir, null, baseDir.getPath());
      if (process != null) {
        // Wait for process to finish. (We overwrite some files, so make sure we lose the race.)
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

    runFlutterCreate(sdk, baseDir, model.getModule());

    try {
      DartPlugin.ensureDartSdkConfigured(model.getProject(), sdk.getDartSdkPath());
      FlutterSdkUtil.updateKnownSdkPaths(sdk.getHomePath());
    }
    catch (ExecutionException e) {
      LOG.warn("Error configuring the Flutter SDK.", e);
      throw new ConfigurationException("Error configuring Flutter SDK");
    }
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
