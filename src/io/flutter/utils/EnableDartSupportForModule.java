/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.EdtInvocationManager;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.sdk.DartSdk;
import com.jetbrains.lang.dart.sdk.DartSdkLibUtil;
import com.jetbrains.lang.dart.sdk.DartSdkUtil;
import org.jetbrains.annotations.NotNull;

// Adapted from private class com.jetbrains.lang.dart.ide.actions.DartEditorNotificationsProvider.EnableDartSupportForModule.
class EnableDartSupportForModule implements Runnable {
  private final Module myModule;

  EnableDartSupportForModule(@NotNull final Module module) {
    this.myModule = module;
  }

  @Override
  public void run() {
    final Project project = myModule.getProject();

    EdtInvocationManager.getInstance().invokeLater(() -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        ApplicationManager.getApplication().runWriteAction(() -> {
          if (DartSdk.getDartSdk(project) == null || !DartSdkLibUtil.isDartSdkEnabled(myModule)) {
            final String sdkPath = DartSdkUtil.getFirstKnownDartSdkPath();
            if (DartSdkUtil.isDartSdkHome(sdkPath)) {
              DartSdkLibUtil.ensureDartSdkConfigured(project, sdkPath);
              DartSdkLibUtil.enableDartSdk(myModule);
            }
            else {
              FlutterModuleUtils.enableDartSDK(myModule);
            }
          }
        });
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          ApplicationManager.getApplication().runReadAction(() -> {
            DartAnalysisServerService.getInstance(project).serverReadyForRequest(project);
          });
        });
      });
    });
  }
}