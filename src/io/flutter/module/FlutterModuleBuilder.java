/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.module;

import com.google.common.collect.ImmutableList;
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
import io.flutter.FlutterConstants;
import io.flutter.FlutterUtils;
import io.flutter.pub.PubRoot;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Arrays.asList;

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
    if (sdk == null) {
      Messages.showErrorDialog("Flutter SDK not found", "Error");
      return null;
    }
    final PubRoot root = runFlutterCreateWithProgress(baseDir, sdk, project);
    if (root == null) {
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

    FlutterModuleUtils.autoShowMain(project, root);

    final Module android = addAndroidModule(project, model, basePath, flutter.getName());
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
                                  @NotNull String baseDirPath,
                                  @NotNull String flutterModuleName) {
    final VirtualFile baseDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(baseDirPath);
    if (baseDir == null) {
      return null;
    }

    final VirtualFile androidFile = findAndroidModuleFile(baseDir, flutterModuleName);
    if (androidFile == null) return null;

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

  @Nullable
  private VirtualFile findAndroidModuleFile(@NotNull VirtualFile baseDir, String flutterModuleName) {
    baseDir.refresh(false, false);
    for (String name : asList(flutterModuleName + "_android.iml", "android.iml")) {
      final VirtualFile candidate = baseDir.findChild(name);
      if (candidate != null && candidate.exists()) {
        return candidate;
      }
    }
    return null;
  }

  @Override
  public boolean validate(Project current, Project dest) {
    return myStep.getFlutterSdk() != null;
  }

  @Override
  public boolean validateModuleName(@NotNull String moduleName) throws ConfigurationException {

    // See: https://www.dartlang.org/tools/pub/pubspec#name

    if (!FlutterUtils.isValidPackageName(moduleName)) {
      throw new ConfigurationException("Invalid module name: '" + moduleName + "' - must be a valid Dart package name (lower_case_with_underscores).");
    }

    if (FlutterUtils.isDartKeword(moduleName)) {
      throw new ConfigurationException("Invalid module name: '" + moduleName + "' - must not be a Dart keyword.");
    }

    if (!FlutterUtils.isValidDartIdentifier(moduleName)) {
      throw new ConfigurationException("Invalid module name: '" + moduleName + "' - must be a valid Dart identifier.");
    }

    if (FlutterConstants.FLUTTER_PACKAGE_DEPENDENCIES.contains(moduleName)) {
      throw new ConfigurationException("Invalid module name: '" + moduleName + "' - this will conflict with Flutter package dependencies.");
    }

    if (moduleName.length() > FlutterConstants.MAX_MODULE_NAME_LENGTH) {
      throw new ConfigurationException("Invalid module name - must be less than " +
                                       FlutterConstants.MAX_MODULE_NAME_LENGTH +
                                       " characters.");
    }

    return super.validateModuleName(moduleName);
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
   * <p>
   * Returns the PubRoot if successful.
   */
  @Nullable
  private static PubRoot runFlutterCreateWithProgress(@NotNull VirtualFile baseDir,
                                                      @NotNull FlutterSdk sdk,
                                                      @NotNull Project project) {
    final ProgressManager progress = ProgressManager.getInstance();
    final AtomicReference<PubRoot> result = new AtomicReference<>(null);

    progress.runProcessWithProgressSynchronously(() -> {
      progress.getProgressIndicator().setIndeterminate(true);
      result.set(sdk.createFiles(baseDir, null));
    }, "Creating Flutter Project", false, project);

    return result.get();
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

    @Nullable
    public FlutterSdk getFlutterSdk() {
      return FlutterSdk.forPath(peer.getSdkComboPath());
    }
  }
}
