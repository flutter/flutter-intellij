/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.sdk;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.libraries.ApplicationLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import io.flutter.FlutterProjectComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class FlutterSdk {
  public static final String FLUTTER_SDK_GLOBAL_LIB_NAME = "Flutter SDK";
  private static final String UNKNOWN_VERSION = "unknown";
  private static final Key<CachedValue<FlutterSdk>> CACHED_FLUTTER_SDK_KEY = Key.create("CACHED_FLUTTER_SDK_KEY");

  private final @NotNull String myHomePath;
  private final @NotNull String myVersion;

  private FlutterSdk(@NotNull final String homePath, @NotNull final String version) {
    myHomePath = homePath;
    myVersion = version;
  }

  /**
   * Returns the same as {@link #getGlobalFlutterSdk()} but much faster
   */
  @Nullable
  public static FlutterSdk getFlutterSdk(@NotNull final Project project) {
    CachedValue<FlutterSdk> cachedValue = project.getUserData(CACHED_FLUTTER_SDK_KEY);

    if (cachedValue == null) {
      cachedValue = CachedValuesManager.getManager(project).createCachedValue(() -> {
        final FlutterSdk sdk = getGlobalFlutterSdk();
        if (sdk == null) {
          return new CachedValueProvider.Result<>(null, FlutterProjectComponent.getProjectRootsModificationTracker(project));
        }

        List<Object> dependencies = new ArrayList<>(3);
        dependencies.add(FlutterProjectComponent.getProjectRootsModificationTracker(project));
        ContainerUtil
          .addIfNotNull(dependencies, LocalFileSystem.getInstance().findFileByPath(FlutterSdkUtil.versionPath(sdk.getHomePath())));
        ContainerUtil.addIfNotNull(dependencies, LocalFileSystem.getInstance().findFileByPath(sdk.getHomePath() + "/bin/flutter"));

        return new CachedValueProvider.Result<>(sdk, ArrayUtil.toObjectArray(dependencies));
      }, false);

      project.putUserData(CACHED_FLUTTER_SDK_KEY, cachedValue);
    }

    return cachedValue.getValue();
  }

  @Nullable
  public static FlutterSdk getGlobalFlutterSdk() {
    return findFlutterSdkAmongGlobalLibs(ApplicationLibraryTable.getApplicationTable().getLibraries());
  }

  @Nullable
  public static FlutterSdk findFlutterSdkAmongGlobalLibs(final Library[] globalLibraries) {
    for (final Library library : globalLibraries) {
      if (FLUTTER_SDK_GLOBAL_LIB_NAME.equals(library.getName())) {
        return getSdkByLibrary(library);
      }
    }

    return null;
  }

  @Nullable
  static FlutterSdk getSdkByLibrary(@NotNull final Library library) {
    final VirtualFile[] roots = library.getFiles(OrderRootType.CLASSES);
    if (roots.length == 1) {
      final VirtualFile flutterSdkRoot = findFlutterSdkRoot(roots[0]);
      if (flutterSdkRoot != null) {
        final String homePath = flutterSdkRoot.getPath();
        final String version = StringUtil.notNullize(FlutterSdkUtil.getSdkVersion(homePath), UNKNOWN_VERSION);
        return new FlutterSdk(homePath, version);
      }
    }

    return null;
  }

  private static VirtualFile findFlutterSdkRoot(VirtualFile dartSdkLibDir) {
    // Navigating up from `bin/cache/dart-sdk/lib/`
    int count = 4;
    VirtualFile parent = dartSdkLibDir;
    do {
      parent = parent.getParent();
    }
    while (parent != null && --count > 0);
    return parent != null && parent.getName().equals("flutter") ? parent : null;
  }

  @NotNull
  public String getHomePath() {
    return myHomePath;
  }

  /**
   * @return presentable version with revision, like ea7d5bf291
   */
  @NotNull
  public String getVersion() {
    return myVersion;
  }
}
