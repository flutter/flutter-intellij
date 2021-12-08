/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.util.lang.UrlClassLoader;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;

public class FileUtils {
  private static FileUtils fileUtils;

  public static FileUtils getInstance() {
    if (fileUtils == null) {
      fileUtils = new FileUtils();
    }
    return fileUtils;
  }

  /**
   * Makes a directory at the provided path.
   * @param path path of the directory to be created.
   * @return true if the directory already existed, or if it was successfully created; false if the directory could not be created.
   */
  public boolean makeDirectory(String path) {
    final File directory = new File(path);
    if (!directory.exists()) {
      return directory.mkdirs();
    }
    return true;
  }

  public boolean fileExists(String path) {
    final File file = new File(path);
    return file.exists();
  }

  /**
   * Deletes a file at the provided path.
   * @param path path of the file to be deleted.
   * @return true if the file does not exist, or if it was successfully deleted; false if the file could not be deleted.
   */
  public boolean deleteFile(String path) {
    final File file = new File(path);
    if (file.exists()) {
      return file.delete();
    }
    return true;
  }

  public void loadClass(ClassLoader classLoader, String path) throws Exception {
    final UrlClassLoader urlClassLoader = (UrlClassLoader) classLoader;

    final File file = new File(path);
    if (!file.exists()) {
      throw new Exception("File does not exist: " + file.getAbsolutePath());
    }

    final URL url = file.toURI().toURL();
    urlClassLoader.addURL(url);
  }

  /**
   * Loads a list of file paths with a class loader.
   *
   * This is only available for versions 211.4961.30 and later.
   * @param classLoader classloader that can be used as a UrlClassLoader to load the files.
   * @param paths list of file paths to load.
   */
  public void loadPaths(ClassLoader classLoader, List<Path> paths) {
    final UrlClassLoader urlClassLoader = (UrlClassLoader) classLoader;
    urlClassLoader.addFiles(paths);
  }
}
