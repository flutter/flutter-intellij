/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Version;
import com.jetbrains.lang.dart.sdk.DartSdk;
import com.jetbrains.lang.dart.sdk.DartSdkGlobalLibUtil;
import com.jetbrains.lang.dart.sdk.DartSdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Provides access to the Dart Plugin.
 */
public class DartPlugin {

  /**
   * Tracks the minimum required Dart Plugin version.
   */
  //TODO: update to 163.10154 once our IDE lowerbound bumps to 2016.3.2
  private static final String MINIMUM_REQUIRED_PLUGIN_VERSION = "162.2485";
  private static final Version MINIMUM_VERSION = Version.parseVersion(MINIMUM_REQUIRED_PLUGIN_VERSION);

  private static final DartPlugin INSTANCE = new DartPlugin();

  private Version myVersion;

  public static DartPlugin getInstance() {
    return INSTANCE;
  }

  @Nullable
  public static DartSdk getDartSdk(@NotNull Project project) {
    return DartSdk.getDartSdk(project);
  }

  public static boolean isDartSdkEnabled(@NotNull Module module) {
    return DartSdkGlobalLibUtil.isDartSdkEnabled(module);
  }

  public static void enableDartSdk(@NotNull Module module) {
    DartSdkGlobalLibUtil.enableDartSdk(module);
  }

  public static void ensureDartSdkConfigured(@NotNull String sdkHomePath) {
    DartSdkGlobalLibUtil.ensureDartSdkConfigured(sdkHomePath);
  }

  public static void disableDartSdk(@NotNull Collection<Module> modules) {
    DartSdkGlobalLibUtil.disableDartSdk(modules);
  }

  public static boolean isDartSdkHome(@Nullable String path) {
    return DartSdkUtil.isDartSdkHome(path);
  }

  /**
   * @return the minimum required version of the Dart Plugin
   */
  public Version getMinimumVersion() {
    return MINIMUM_VERSION;
  }

  /**
   * @return the version of the currently installed Dart Plugin
   */
  public Version getVersion() {
    if (myVersion == null) {
      final IdeaPluginDescriptor descriptor = PluginManager.getPlugin(PluginId.getId("Dart"));
      assert (descriptor != null);
      myVersion = Version.parseVersion(descriptor.getVersion());
    }
    return myVersion;
  }
}
