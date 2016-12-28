/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterBundle;
import io.flutter.FlutterErrors;
import io.flutter.FlutterInitializer;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;

public class FlutterPackagesGetAction extends FlutterSdkAction {

  private static final FlutterSdk.Command COMMAND = FlutterSdk.Command.PACKAGES_GET;

  @Override
  public void perform(@NotNull FlutterSdk sdk, @NotNull Project project, AnActionEvent event) throws ExecutionException {
    FlutterInitializer.sendActionEvent(this);

    final Pair<Module, VirtualFile> pair = getModuleAndPubspecYamlFile(project, event);
    if (pair != null) {
      sdk.run(COMMAND, pair.first, pair.second.getParent(), null);
    }
    else {
      FlutterErrors.showError(
        FlutterBundle.message("flutter.command.missing.pubspec"),
        FlutterBundle.message("flutter.command.missing.pubspec.message", COMMAND.title));
    }
  }
}
