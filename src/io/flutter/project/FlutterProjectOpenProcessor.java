/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.FlutterMessages;
import io.flutter.FlutterUtils;
import io.flutter.ProjectOpenActivity;
import io.flutter.pub.PubRoot;
import io.flutter.utils.FlutterModuleUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Objects;

public class FlutterProjectOpenProcessor extends ProjectOpenProcessor {
  private static final Logger LOG = Logger.getInstance(FlutterProjectOpenProcessor.class);

  private static void handleError(@NotNull Exception e) {
    FlutterMessages.showError("Error opening", e.getMessage());
  }

  @Override
  public String getName() {
    return FlutterBundle.message("flutter.module.name");
  }

  @Override
  public Icon getIcon() {
    return FlutterIcons.Flutter;
  }

  @Override
  public boolean canOpenProject(@Nullable VirtualFile file) {
    if (file == null) return false;
    final ApplicationInfo info = ApplicationInfo.getInstance();
    if (FlutterUtils.isAndroidStudio()) {
      return false;
    }
    final PubRoot root = PubRoot.forDirectory(file);
    return root != null && root.declaresFlutter();
  }

  /**
   * Runs when a project is opened by selecting the project directly, possibly for import.
   * <p>
   * Doesn't run when a project is opened via recent projects menu (and so on). Actions that
   * should run every time a project is opened should be in
   * {@link ProjectOpenActivity} or {@link io.flutter.FlutterInitializer}.
   */
  @Nullable
  @Override
  public Project doOpenProject(@NotNull VirtualFile file, @Nullable Project projectToClose, boolean forceOpenInNewFrame) {
    // Delegate opening to the platform open processor.
    final ProjectOpenProcessor importProvider = getDelegateImportProvider(file);
    if (importProvider == null) return null;

    final Project project = importProvider.doOpenProject(file, projectToClose, forceOpenInNewFrame);
    if (project == null || project.isDisposed()) return project;

    // Convert any modules that use Flutter but don't have IntelliJ Flutter metadata.
    convertToFlutterProject(project);

    return project;
  }

  @Nullable
  private ProjectOpenProcessor getDelegateImportProvider(@Nullable VirtualFile file) {
    return Arrays.stream(Extensions.getExtensions(EXTENSION_POINT_NAME)).filter(
      processor -> processor.canOpenProject(file) && !Objects.equals(processor.getName(), getName())
    ).findFirst().orElse(null);
  }

  /**
   * Sets up a project that doesn't have any Flutter modules.
   * <p>
   * (It probably wasn't created with "flutter create" and probably didn't have any IntelliJ configuration before.)
   */
  private static void convertToFlutterProject(@NotNull Project project) {
    for (Module module : FlutterModuleUtils.getModules(project)) {
      if (FlutterModuleUtils.declaresFlutter(module) && !FlutterModuleUtils.isFlutterModule(module)) {
        FlutterModuleUtils.setFlutterModuleAndReload(module, project);
      }
    }
  }

  @Override
  public boolean isStrongProjectInfoHolder() {
    return true;
  }
}
