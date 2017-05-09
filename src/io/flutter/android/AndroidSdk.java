/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.android;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * An Android SDK and its home directory.
 */
public class AndroidSdk {
  @NotNull
  private final Sdk sdk;

  @NotNull
  private final VirtualFile home;

  private AndroidSdk(@NotNull Sdk sdk, @NotNull VirtualFile home) {
    this.sdk = sdk;
    this.home = home;
  }

  /**
   * Returns android home directory for this SDK.
   */
  @NotNull
  public VirtualFile getHome() {
    return home;
  }

  /**
   * Changes the project's Java SDK to this one.
   */
  public void setCurrent(@NotNull Project project) {
    assert ApplicationManager.getApplication().isWriteAccessAllowed();

    final ProjectRootManager roots = ProjectRootManager.getInstance(project);
    roots.setProjectSdk(sdk);
  }

  /**
   * Returns the Android SDK that matches the ANDROID_HOME environment variable, provided it exists.
   */
  @Nullable
  public static AndroidSdk fromEnvironment() {
    final String path = EnvironmentUtil.getValue("ANDROID_HOME");
    if (path == null) {
      return null;
    }

    // TODO(skybrian) refresh?
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    if (file == null) {
      return null;
    }

    for (AndroidSdk candidate : findAll()) {
      if (file.equals(candidate.getHome())) {
        return candidate;
      }
    }

    return null; // not found
  }

  /**
   * Returns each SDK that's an Android SDK.
   */
  @NotNull
  private static List<AndroidSdk> findAll() {
    final List<AndroidSdk> result = new ArrayList<>();
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      final AndroidSdk candidate = AndroidSdk.fromSdk(sdk);
      if (candidate != null) {
        result.add(candidate);
      }
    }
    return result;
  }

  @Nullable
  private static AndroidSdk fromSdk(@Nullable Sdk candidate) {
    if (candidate == null) {
      return null;
    }

    if (!"Android SDK".equals(candidate.getSdkType().getName())) {
      return null;
    }

    final VirtualFile home = candidate.getHomeDirectory();
    if (home == null) {
      return null; // Skip; misconfigured SDK?
    }

    return new AndroidSdk(candidate, home);
  }
}
