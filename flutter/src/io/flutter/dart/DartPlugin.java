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
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.ide.actions.DartPubActionBase;
import com.jetbrains.lang.dart.sdk.DartSdk;
import com.jetbrains.lang.dart.sdk.DartSdkLibUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A service component that manages Dart plugin functionality within IntelliJ IDEA.
 * <p>
 * This class provides project-level services for Dart integration, including version
 * management and feature compatibility checks. It acts as a central point for
 * accessing Dart plugin capabilities and configurations within a project.
 * <p>
 * The service is automatically instantiated by the IntelliJ Platform on a per-project
 * basis and can be accessed via getInstance(Project).
 */
public class DartPlugin {

  @NotNull
  private static final DartPlugin INSTANCE = new DartPlugin();

  @NotNull
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

  public static boolean isPubActionInProgress() {
    return DartPubActionBase.isInProgress();
  }

  public static void setPubActionInProgress(boolean inProgress) {
    DartPubActionBase.setIsInProgress(inProgress);
  }

  public static boolean isDartRunConfiguration(@NotNull ConfigurationType type) {
    return type.getId().equals("DartCommandLineRunConfigurationType");
  }

  public static boolean isDartTestConfiguration(@NotNull ConfigurationType type) {
    return type.getId().equals("DartTestRunConfigurationType");
  }

  /**
   * Returns the version of the Dart plugin installed in the IDE.
   */
  @NotNull
  public static DartPluginVersion getDartPluginVersion() {
    final IdeaPluginDescriptor dartPlugin = PluginManagerCore.getPlugin(PluginId.getId("Dart"));
    final String versionString = dartPlugin != null ? dartPlugin.getVersion() : null;
    return new DartPluginVersion(versionString);
  }

  /**
   * Return the {@link DartAnalysisServerService} instance for the passed {@link Project}.
   */
  @Nullable
  public DartAnalysisServerService getAnalysisService(@NotNull final Project project) {
    return project.getService(DartAnalysisServerService.class);
  }
}
