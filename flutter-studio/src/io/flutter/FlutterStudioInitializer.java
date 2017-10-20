/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter;

import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.ui.Messages;

public class FlutterStudioInitializer implements Runnable {

  private static void reportVersionIncompatibility(ApplicationInfo info) {
    Messages.showErrorDialog("The Flutter plugin requires a more recent version of Android Studio.", "Version Mismatch");
  }

  @Override
  public void run() {
    // Unlike StartupActivity, this runs before the welcome screen (FlatWelcomeFrame) is displayed.
    // StartupActivity runs just before a project is opened.
    ApplicationInfo info = ApplicationInfo.getInstance();
    if ("Google".equals(info.getCompanyName())) {
      String version = info.getFullVersion();
      if (version.startsWith("2.") || version.contains("Canary") || (version.contains("Beta") && !version.endsWith("7"))) {
        reportVersionIncompatibility(info);
      }
    }
  }
}
