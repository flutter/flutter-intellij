/*
 * Copyright 2019 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterMessages;
import io.flutter.pub.PubRoot;
import io.flutter.sdk.FlutterSdk;
import io.flutter.utils.FlutterModuleUtils;
import io.flutter.utils.ProgressHelper;
import org.jetbrains.annotations.NotNull;

public class FlutterBuildActionGroup extends DefaultActionGroup {

  public static OSProcessHandler build(Project project, PubRoot pubRoot, FlutterSdk sdk, BuildType buildType, String desc) {
    ProgressHelper progressHelper = new ProgressHelper(project);
    progressHelper.start(desc);
    OSProcessHandler processHandler = sdk.flutterBuild(pubRoot, buildType.type).startInConsole(project);
    if (processHandler == null) {
      progressHelper.done();
    }
    else {
      processHandler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(@NotNull ProcessEvent event) {
          progressHelper.done();
          int exitCode = event.getExitCode();
          if (exitCode != 0) {
            FlutterMessages.showError("Error while building " + buildType, "`flutter build` returned: " + exitCode);
          }
        }
      });
    }
    return processHandler;
  }

  public enum BuildType {
    AAR("aar"),
    APK("apk"),
    APP_BUNDLE("appbundle"),
    IOS("ios");

    final public String type;

    BuildType(String type) {
      this.type = type;
    }
  }

  @Override
  public void update(AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    final boolean enabled = isInFlutterModule(event);
    presentation.setEnabled(enabled);
    presentation.setVisible(enabled);
  }

  private static boolean isInFlutterModule(@NotNull AnActionEvent event) {
    return findFlutterModule(event) != null;
  }

  private static Module findFlutterModule(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    if (project == null) {
      return null;
    }
    VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
    if (file == null) {
      return null;
    }
    return findFlutterModule(project, file);
  }

  public static Module findFlutterModule(@NotNull Project project, @NotNull VirtualFile file) {
    Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (module == null) {
      return null;
    }
    if (FlutterModuleUtils.declaresFlutter(module)) {
      return module;
    }
    // We may get here if the file is in the Android module of a Flutter module project.
    VirtualFile parent = ModuleRootManager.getInstance(module).getContentRoots()[0].getParent();
    module = ModuleUtilCore.findModuleForFile(parent, project);
    if (module == null) {
      return null;
    }
    return FlutterModuleUtils.declaresFlutter(module) ? module : null;
  }

  abstract public static class FlutterBuildAction extends AnAction {

    abstract protected BuildType buildType();

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      Presentation presentation = event.getPresentation();
      Project project = event.getProject();
      if (project == null) {
        return;
      }
      FlutterSdk sdk = FlutterSdk.getFlutterSdk(project);
      if (sdk == null) {
        return;
      }
      PubRoot pubRoot = PubRoot.forEventWithRefresh(event);
      if (pubRoot == null) {
        return;
      }
      BuildType buildType = buildType();
      build(project, pubRoot, sdk, buildType, presentation.getDescription());
    }
  }

  public static class AAR extends FlutterBuildAction {
    @Override
    protected BuildType buildType() {
      return BuildType.AAR;
    }
  }

  public static class APK extends FlutterBuildAction {
    @Override
    protected BuildType buildType() {
      return BuildType.APK;
    }
  }

  public static class AppBundle extends FlutterBuildAction {
    @Override
    protected BuildType buildType() {
      return BuildType.APP_BUNDLE;
    }
  }

  public static class Ios extends FlutterBuildAction {
    @Override
    protected BuildType buildType() {
      return BuildType.IOS;
    }
  }
}
