/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.project;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;
import icons.FlutterIcons;
import io.flutter.FlutterBundle;
import io.flutter.FlutterErrors;
import io.flutter.FlutterUtils;
import io.flutter.actions.FlutterPackagesGetAction;
import io.flutter.actions.FlutterSdkAction;
import io.flutter.sdk.FlutterSdk;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Objects;


public class FlutterProjectOpenProcessor extends ProjectOpenProcessor {

  private static void doPerform(@NotNull FlutterSdkAction action, @NotNull Project project) throws ExecutionException {
    final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
    if (sdk == null) {
      // Lack of SDK will be flagged elsewhere by inspection.
      return;
    }

    action.perform(sdk, project, null);
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
    return FlutterSdkUtil.isFlutterProjectDir(file);
  }

  @Nullable
  @Override
  public Project doOpenProject(@NotNull VirtualFile file, @Nullable Project projectToClose, boolean forceOpenInNewFrame) {

    // Delegate opening to the platform open processor.
    final ProjectOpenProcessor importProvider = getDelegateImportProvider(file);
    if (importProvider == null) {
      return null;
    }

    final Project project = importProvider.doOpenProject(file, projectToClose, forceOpenInNewFrame);
    if (project == null) {
      return null;
    }

    // Once open, perform post open processing.
    doPostOpenProcessing(project);

    return project;
  }

  private void doPostOpenProcessing(@NotNull Project project) {
    try {
      final VirtualFile packagesFile = FlutterSdkUtil.findPackagesFileFrom(project, null);
      if (!FlutterUtils.exists(packagesFile)) {
        doPerform(new FlutterPackagesGetAction(), project);
      }
      //TODO(pq): Check if .packages is out of date and offer to run `packages update`.
      //else {
      //
      //}
    }
    catch (ExecutionException e) {
      FlutterErrors.showError("Error opening",
                              e.getMessage());
    }
  }

  @Nullable
  private ProjectOpenProcessor getDelegateImportProvider(@Nullable VirtualFile file) {
    return Arrays.stream(Extensions.getExtensions(EXTENSION_POINT_NAME)).filter(
      processor -> processor.canOpenProject(file) && !Objects.equals(processor.getName(), getName())
    ).findFirst().orElse(null);
  }

  @Override
  public boolean isStrongProjectInfoHolder() {
    return true;
  }
}
