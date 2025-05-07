/*
 * Copyright 2020 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.utils;

import com.intellij.util.lang.UrlClassLoader;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import org.jetbrains.annotations.NotNull;

public class FileUtils {
  
  private FileUtils() {
    throw new AssertionError("No instances.");
  }

  /**
   * Makes a directory at the provided path.
   *
   * @param path path of the directory to be created.
   * @return true if the directory already existed, or if it was successfully created; false if the directory could not be created.
   */
  public static boolean makeDirectory(@NotNull String path) {
    final File directory = new File(path);
    if (!directory.exists()) {
      return directory.mkdirs();
    }
    return true;
  }

  public static boolean fileExists(@NotNull String path) {
    final File file = new File(path);
    return file.exists();
  }

  /**
   * Deletes a file at the provided path.
   *
   * @param path path of the file to be deleted.
   * @return true if the file does not exist, or if it was successfully deleted; false if the file could not be deleted.
   */
  public static boolean deleteFile(@NotNull String path) {
    final File file = new File(path);
    if (file.exists()) {
      return file.delete();
    }
    return true;
  }

  /**
   * Loads a list of file paths with a class loader.
   * <p>
   * This is only available for versions 211.4961.30 and later.
   *
   * @param classLoader classloader that can be used as an UrlClassLoader to load the files.
   * @param paths       list of file paths to load.
   */
  public static void loadPaths(@NotNull ClassLoader classLoader, @NotNull List<Path> paths) {
    final UrlClassLoader urlClassLoader = (UrlClassLoader)classLoader;
    urlClassLoader.addFiles(paths);
  }
}
