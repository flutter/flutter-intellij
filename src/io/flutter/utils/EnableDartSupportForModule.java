// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package io.flutter.utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.sdk.DartSdk;
import com.jetbrains.lang.dart.sdk.DartSdkLibUtil;
import com.jetbrains.lang.dart.sdk.DartSdkUtil;
import org.jetbrains.annotations.NotNull;

// Copied from private class com.jetbrains.lang.dart.ide.actions.DartEditorNotificationsProvider.EnableDartSupportForModule.
class EnableDartSupportForModule implements Runnable {
  private final Module myModule;

  EnableDartSupportForModule(@NotNull final Module module) {
    this.myModule = module;
  }

  @Override
  public void run() {
    final Project project = myModule.getProject();

    ApplicationManager.getApplication().runWriteAction(() -> {
      if (DartSdk.getDartSdk(project) == null) {
        final String sdkPath = DartSdkUtil.getFirstKnownDartSdkPath();
        if (DartSdkUtil.isDartSdkHome(sdkPath)) {
          DartSdkLibUtil.ensureDartSdkConfigured(project, sdkPath);
        }
        else {
          return; // shouldn't happen, sdk path is already checked
        }
      }

      DartSdkLibUtil.enableDartSdk(myModule);
    });

    DartAnalysisServerService.getInstance(project).serverReadyForRequest();
    // At this point the original provides a notification of success or failure, which we don't need.
  }
}