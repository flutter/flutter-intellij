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
  /**
   * Makes a directory at the provided path.
   * @param path path of the directory to be created.
   * @return true if the directory already existed, or if it was successfully created; false if the directory could not be created.
   */
  public static boolean makeDirectory(String path) {
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

  /**
   * Deletes a file at the provided path.
   * @param path path of the file to be deleted.
   * @return true if the file does not exist, or if it was successfully deleted; false if the file could not be deleted.
   */
  public static boolean deleteFile(String path) {
    final File file = new File(path);
    if (file.exists()) {
      return file.delete();
    }
    return true;
  }

  public static boolean loadClass(ClassLoader classLoader, String path) {
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
