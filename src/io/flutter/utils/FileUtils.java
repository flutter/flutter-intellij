/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.util.lang.UrlClassLoader;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

public class FileUtils {
  public static boolean makeDirectoryIfNotExists(String path) {
    final File directory = new File(path);
    if (!directory.exists()) {
      return directory.mkdirs();
    }
    return true;
  }

  public static boolean fileExists(String path) {
    final File file = new File(path);
    return file.exists();
  }

  public static boolean deleteFileIfExists(String path) {
    final File file = new File(path);
    if (file.exists()) {
      return file.delete();
    }
    return false;
  }

  public static boolean loadClassWithClassLoader(ClassLoader classLoader, String path) {
    final UrlClassLoader urlClassLoader = (UrlClassLoader) classLoader;
    final File file = new File(path);
    final URL url;
    try {
      url = file.toURI().toURL();
    }
    catch (MalformedURLException e) {
      return false;
    }
    urlClassLoader.addURL(url);
    return true;
  }
}
