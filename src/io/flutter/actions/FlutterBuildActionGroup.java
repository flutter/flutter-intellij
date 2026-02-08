/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.execution.process.ColoredProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.lang.dart.analytics.Analytics;
import com.jetbrains.lang.dart.analytics.AnalyticsData;
import io.flutter.FlutterMessages;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRoots;
import io.flutter.sdk.FlutterSdk;
import io.flutter.utils.FlutterModuleUtils;
import io.flutter.utils.OpenApiUtils;
import io.flutter.utils.ProcessAdapter;
import io.flutter.utils.ProgressHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FlutterBuildActionGroup extends DefaultActionGroup {

  public static void build(@NotNull Project project,
                           @NotNull PubRoot pubRoot,
                           @NotNull FlutterSdk sdk,
                           @NotNull BuildType buildType,
                           @Nullable String desc) {
    final ProgressHelper progressHelper = new ProgressHelper(project);
    progressHelper.start(desc == null ? "building" : desc);
    ProcessAdapter processAdapter = new ProcessAdapter() {
      @Override
      public void processTerminated(@NotNull ProcessEvent event) {
        progressHelper.done();
        final int exitCode = event.getExitCode();
        if (exitCode != 0) {
          FlutterMessages.showError("Error while building " + buildType, "`flutter build` returned: " + exitCode, project);
        }
      }
    };
    final Module module = pubRoot.getModule(project);
    if (module != null) {
      sdk.flutterBuild(pubRoot, buildType.type).startInModuleConsole(module, pubRoot::refresh, processAdapter);
    }
    else {
      final ColoredProcessHandler processHandler = sdk.flutterBuild(pubRoot, buildType.type).startInConsole(project);
      if (processHandler == null) {
        progressHelper.done();
      }
      else {
        processHandler.addProcessListener(processAdapter);
      }
    }
  }

  public enum BuildType {
    AAR("aar"),
    APK("apk"),
    APP_BUNDLE("appbundle"),
    IOS("ios"),
    WEB("web");

    final public String type;

    BuildType(String type) {
      this.type = type;
    }
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    final boolean enabled = isInFlutterModule(event);
    presentation.setEnabled(enabled);
    presentation.setVisible(enabled);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static boolean isInFlutterModule(@NotNull AnActionEvent event) {
    final Project project = event.getProject();
    if (project == null) {
      return false;
    }
    return FlutterModuleUtils.hasFlutterModule(project);
  }

  @Nullable
  public static Module findFlutterModule(@NotNull Project project, @NotNull VirtualFile file) {
    Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (module == null) {
      return null;
    }
    if (FlutterModuleUtils.declaresFlutter(module)) {
      return module;
    }
    // We may get here if the file is in the Android module of a Flutter module project.
    var root = OpenApiUtils.getContentRoots(module)[0];
    if (root == null) return null;

    final VirtualFile parent = root.getParent();
    if (parent == null) return null;

    module = ModuleUtilCore.findModuleForFile(parent, project);
    if (module == null) {
      return null;
    }
    return FlutterModuleUtils.declaresFlutter(module) ? module : null;
  }

  abstract public static class FlutterBuildAction extends AnAction {

    public static String ID_PREFIX = "flutter.build";

    @NotNull
    abstract protected BuildType buildType();

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      final Presentation presentation = event.getPresentation();
      final Project project = event.getProject();
      if (project == null) {
        return;
      }

      Analytics.report(AnalyticsData.forAction(ID_PREFIX + "." + buildType().type, event.getPlace(), project));

      final FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
      if (sdk == null) {
        return;
      }
      final PubRoot pubRoot = PubRoot.forEventWithRefresh(event);
      final BuildType buildType = buildType();
      if (pubRoot != null) {
        build(project, pubRoot, sdk, buildType, presentation.getDescription());
      }
      else {
        var roots = PubRoots.forProject(project);
        for (PubRoot sub : roots) {
          build(project, sub, sdk, buildType, presentation.getDescription());
        }
      }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }

  public static class AAR extends FlutterBuildAction {
    @Override
    protected @NotNull BuildType buildType() {
      return BuildType.AAR;
    }
  }

  public static class APK extends FlutterBuildAction {
    @Override
    protected @NotNull BuildType buildType() {
      return BuildType.APK;
    }
  }

  public static class AppBundle extends FlutterBuildAction {
    @Override
    protected @NotNull BuildType buildType() {
      return BuildType.APP_BUNDLE;
    }
  }

  public static class Ios extends FlutterBuildAction {
    @Override
    protected @NotNull BuildType buildType() {
      return BuildType.IOS;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
      final Presentation presentation = event.getPresentation();
      presentation.setEnabled(SystemInfo.isMac);
    }
  }

  public static class Web extends FlutterBuildAction {

    @Override
    protected @NotNull BuildType buildType() {
      return BuildType.WEB;
    }
  }
}
