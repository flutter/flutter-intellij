/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import java.io.File;

public class FileUtils {
  public static boolean makeDirectoryIfNotExists(String path) {
    final File directory = new File(path);
    if (!directory.exists()) {
      return directory.mkdirs();
    }
    return true;
  }
}
