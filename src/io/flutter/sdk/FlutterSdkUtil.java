/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.execution.ExecutionException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class FlutterSdkUtil {
  private static final Map<Pair<File, Long>, String> ourVersions = new HashMap<>();

  private FlutterSdkUtil() {
  }

  @NotNull
  public static String pathToFlutterTool(@NotNull String sdkPath) throws ExecutionException {
    VirtualFile sdk = LocalFileSystem.getInstance().findFileByPath(sdkPath);
    if (sdk == null) throw new ExecutionException(FlutterBundle.message("flutter.sdk.is.not.configured"));
    VirtualFile bin = sdk.findChild("bin");
    if (bin == null) throw new ExecutionException(FlutterBundle.message("flutter.sdk.is.not.configured"));
    VirtualFile exec = bin.findChild("flutter"); // TODO Use flutter.bat on Windows
    if (exec == null) throw new ExecutionException(FlutterBundle.message("flutter.sdk.is.not.configured"));
    return exec.getPath();
  }

  public static boolean isFlutterSdkLibRoot(@Nullable VirtualFile sdk) {
    if (sdk == null) return false;
    VirtualFile bin = sdk.findChild("bin");
    if (bin == null) return false;
    VirtualFile exec = bin.findChild("flutter");
    if (exec == null) return false;
    return true;
  }

  public static boolean isFlutterSdkHome(@Nullable String path) {
    try {
      return verifyFlutterSdkPath(path) == path;
    }
    catch (ExecutionException ex) {
      return false;
    }
  }

  @NotNull
  public static String verifyFlutterSdkPath(@Nullable String path) throws ExecutionException {
    if (path == null || path.isEmpty()) {
      throw new ExecutionException(FlutterBundle.message("flutter.sdk.is.not.configured"));
    }
    File flutterSdk = new File(path);
    File bin = new File(flutterSdk, "bin");
    if (!bin.isDirectory()) {
      throw new ExecutionException(FlutterBundle.message("flutter.sdk.is.not.configured"));
    }
    if (!(new File(bin, "flutter").exists())) {
      throw new ExecutionException(FlutterBundle.message("flutter.sdk.is.not.configured"));
    }
    return path;
  }

  @NotNull
  public static String versionPath(@NotNull String sdkHomePath) {
    return sdkHomePath + "/VERSION";
  }

  @Nullable
  public static String getSdkVersion(@NotNull String sdkHomePath) {
    File versionFile = new File(versionPath(sdkHomePath));
    if (versionFile.isFile()) {
      String cachedVersion = ourVersions.get(Pair.create(versionFile, versionFile.lastModified()));
      if (cachedVersion != null) return cachedVersion;
    }

    String version = readVersionFile(sdkHomePath);
    if (version != null) {
      ourVersions.put(Pair.create(versionFile, versionFile.lastModified()), version);
      return version;
    }

    return null;
  }

  private static String readVersionFile(String sdkHomePath) {
    File versionFile = new File(versionPath(sdkHomePath));
    if (versionFile.isFile() && versionFile.length() < 1000) {
      try {
        String content = FileUtil.loadFile(versionFile).trim();
        int index = content.lastIndexOf('\n');
        if (index < 0) return content;
        return content.substring(index + 1).trim();
      }
      catch (IOException e) {
        /* ignore */
      }
    }
    return null;
  }
}
