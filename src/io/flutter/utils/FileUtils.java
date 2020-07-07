/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.util.SystemProperties;

public class FileUtils {
  private static final String GOOGLE_DIRECTORY = "Google";
  // This method is a copy of com.intellij.openapi.application.PathManager#platformPath modified to provide the path to the directory
  // containing all JetBrains application files.
  public static String platformPath() {
    // These strings are copied from com.intellij.openapi.application.PathManager#getDefaultConfigPathFor.
    final String macDir = "Application Support";
    final String macSub = "";
    final String winVar = "APPDATA";
    final String winSub = "";
    final String xdgVar = "XDG_CONFIG_HOME";
    final String xdgDfl = ".config";
    final String xdgSub = "";

    // This will be the local directory for storing files needed for the flutter plugin.
    final String selector = "flutter";

    final String userHome = SystemProperties.getUserHome();

    if (SystemInfoRt.isMac) {
      String dir = userHome + "/Library/" + macDir + '/' + GOOGLE_DIRECTORY + '/' + selector;
      if (!macSub.isEmpty()) dir = dir + '/' + macSub;
      return dir;
    }

    if (SystemInfoRt.isWindows) {
      String dir = System.getenv(winVar);
      if (dir == null || dir.isEmpty()) dir = userHome + "\\AppData\\" + (winVar.startsWith("LOCAL") ? "Local" : "Roaming");
      dir = dir + '\\' + GOOGLE_DIRECTORY + '\\' + selector;
      if (!winSub.isEmpty()) dir = dir + '\\' + winSub;
      return dir;
    }

    if (SystemInfoRt.isUnix) {
      String dir = System.getenv(xdgVar);
      if (dir == null || dir.isEmpty()) dir = userHome + '/' + xdgDfl;
      dir = dir + '/' + GOOGLE_DIRECTORY + '/' + selector;
      if (!xdgSub.isEmpty()) dir = dir + '/' + xdgSub;
      return dir;
    }

    throw new UnsupportedOperationException("Unsupported OS: " + SystemInfoRt.OS_NAME);
  }
}
