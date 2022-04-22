/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.dart;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Version;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.ide.actions.DartPubActionBase;
import com.jetbrains.lang.dart.sdk.DartSdk;
import com.jetbrains.lang.dart.sdk.DartSdkLibUtil;
import com.jetbrains.lang.dart.sdk.DartSdkUpdateOption;
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
  private static final Version MINIMUM_VERSION = Version.parseVersion("171.3780.79");

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
    return DartSdkLibUtil.isDartSdkEnabled(module);
  }

  public static void enableDartSdk(@NotNull Module module) {
    DartSdkLibUtil.enableDartSdk(module);
  }

  public static void ensureDartSdkConfigured(@NotNull Project project, @NotNull String sdkHomePath) {
    DartSdkLibUtil.ensureDartSdkConfigured(project, sdkHomePath);
  }

  public static void disableDartSdk(@NotNull Collection<Module> modules) {
    DartSdkLibUtil.disableDartSdk(modules);
  }

  public static boolean isDartSdkHome(@Nullable String path) {
    return DartSdkUtil.isDartSdkHome(path);
  }

  public static boolean isPubActionInProgress() {
    return DartPubActionBase.isInProgress();
  }

  public static void setPubActionInProgress(boolean inProgress) {
    DartPubActionBase.setIsInProgress(inProgress);
  }

  public static boolean isDartRunConfiguration(ConfigurationType type) {
    return type.getId().equals("DartCommandLineRunConfigurationType");
  }

  public static boolean isDartTestConfiguration(ConfigurationType type) {
    return type.getId().equals("DartTestRunConfigurationType");
  }

  public static DartSdkUpdateOption doCheckForUpdates() {
    return DartSdkUpdateOption.getDartSdkUpdateOption();
  }

  public static void setCheckForUpdates(DartSdkUpdateOption sdkUpdateOption) {
    DartSdkUpdateOption.setDartSdkUpdateOption(sdkUpdateOption);
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
      final IdeaPluginDescriptor descriptor = PluginManagerCore.getPlugin(PluginId.getId("Dart"));
      assert (descriptor != null);
      myVersion = Version.parseVersion(descriptor.getVersion());
    }
    return myVersion;
  }

  /**
   * Return the DartAnalysisServerService instance.
   */
  @Nullable
  public DartAnalysisServerService getAnalysisService(@NotNull final Project project) {
    return project.getService(DartAnalysisServerService.class);
  }
}
