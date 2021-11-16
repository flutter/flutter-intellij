/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.android;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EnvironmentUtil;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * An Android SDK and its home directory; this references an IntelliJ @{@link Sdk} instance.
 */
public class IntelliJAndroidSdk {
  private static final Logger LOG = Logger.getInstance(IntelliJAndroidSdk.class);

  @NotNull
  private final Sdk sdk;

  @NotNull
  private final VirtualFile home;

  private IntelliJAndroidSdk(@NotNull Sdk sdk, @NotNull VirtualFile home) {
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
   * Returns the Java SDK in the project's configuration, or null if not an Android SDK.
   */
  @Nullable
  public static IntelliJAndroidSdk fromProject(@NotNull Project project) {
    final Sdk candidate = ProjectRootManager.getInstance(project).getProjectSdk();
    return fromSdk(candidate);
  }

  /**
   * Returns the Android SDK that matches the ANDROID_HOME environment variable, provided it exists.
   */
  @Nullable
  public static IntelliJAndroidSdk fromEnvironment() {
    final String path = EnvironmentUtil.getValue("ANDROID_HOME");
    if (path == null) {
      return null;
    }

    // TODO(skybrian) refresh?
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    if (file == null) {
      return null;
    }

    return fromHome(file);
  }

  /**
   * Returns the Android SDK for the given home directory, or null if no SDK matches.
   */
  @Nullable
  public static IntelliJAndroidSdk fromHome(VirtualFile file) {
    for (IntelliJAndroidSdk candidate : findAll()) {
      if (file.equals(candidate.getHome())) {
        return candidate;
      }
    }

    return null; // not found
  }

  /**
   * Returns the best value of the Android SDK location to use, including possibly querying flutter tools for it.
   */
  public static String chooseAndroidHome(@Nullable Project project, boolean askFlutterTools) {
    if (project == null) {
      return EnvironmentUtil.getValue("ANDROID_HOME");
    }

    final IntelliJAndroidSdk intelliJAndroidSdk = fromProject(project);
    if (intelliJAndroidSdk != null) {
      return intelliJAndroidSdk.getHome().getPath();
    }

    // Ask flutter tools.
    if (askFlutterTools) {
      final FlutterSdk flutterSdk = FlutterSdk.getFlutterSdk(project);
      if (flutterSdk != null) {
        final String androidSdkLocation = flutterSdk.queryFlutterConfig("android-sdk", true);
        if (androidSdkLocation != null) {
          return androidSdkLocation;
        }
      }
    }

    return EnvironmentUtil.getValue("ANDROID_HOME");
  }

  /**
   * Returns each SDK that's an Android SDK.
   */
  @NotNull
  private static List<IntelliJAndroidSdk> findAll() {
    final List<IntelliJAndroidSdk> result = new ArrayList<>();
    for (Sdk sdk : ProjectJdkTable.getInstance().getAllJdks()) {
      final IntelliJAndroidSdk candidate = IntelliJAndroidSdk.fromSdk(sdk);
      if (candidate != null) {
        result.add(candidate);
      }
    }
    return result;
  }

  @Nullable
  private static IntelliJAndroidSdk fromSdk(@Nullable Sdk candidate) {
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

    return new IntelliJAndroidSdk(candidate, home);
  }
}
