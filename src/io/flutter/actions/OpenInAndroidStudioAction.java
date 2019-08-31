/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import io.flutter.FlutterBundle;
import io.flutter.FlutterMessages;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRoots;
import io.flutter.sdk.FlutterSdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class OpenInAndroidStudioAction extends AnAction {
  private static final String LABEL_FILE = FlutterBundle.message("flutter.androidstudio.open.file.text");
  private static final String DESCR_FILE = FlutterBundle.message("flutter.androidstudio.open.file.description");
  private static final String LABEL_MODULE = FlutterBundle.message("flutter.androidstudio.open.module.text");
  private static final String DESCR_MODULE = FlutterBundle.message("flutter.androidstudio.open.module.description");

  @Override
  public void update(AnActionEvent event) {
    updatePresentation(event, event.getPresentation());
  }

  @Override
  public void actionPerformed(AnActionEvent event) {
    final String androidStudioPath = findAndroidStudio(event.getProject());
    if (androidStudioPath == null) {
      FlutterMessages.showError("Unable to locate Android Studio",
                                "You can configure the Android Studio location via " +
                                "'flutter config --android-studio-dir path-to-android-studio'.");
      return;
    }

    final VirtualFile projectFile = findProjectFile(event);
    if (projectFile == null) {
      FlutterMessages.showError("Error Opening Android Studio", "Project not found.");
      return;
    }

    final VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
    final String sourceFile = file == null ? null : file.isDirectory() ? null : file.getPath();
    openFileInStudio(projectFile, androidStudioPath, sourceFile);
  }

  private static void updatePresentation(AnActionEvent event, Presentation state) {
    if (findProjectFile(event) == null) {
      state.setVisible(false);
    }
    else {
      VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);
      String label, descr;
      if (file != null && !file.isDirectory()) {
        // The file will be opened in an editor in the new IDE window.
        label = LABEL_FILE;
        descr = DESCR_FILE;
      }
      else {
        // The new IDE window will be opened on the Android module but there is no file selected for editing.
        label = LABEL_MODULE;
        descr = DESCR_MODULE;
      }
      state.setVisible(true);
      state.setText(label);
      state.setDescription(descr);
    }
  }

  protected static boolean isProjectFileName(String name) {
    // Note: If the project content is rearranged to have the android module file within the android directory, this will fail.
    return name.endsWith("_android.iml");
  }

  // A plugin contains an example app, which needs to be opened when the native Android is to be edited.
  // In the case of an app that contains a plugin the flutter_app/flutter_plugin/example/android should be opened when
  // 'Open in Android Studio' is requested.
  protected static VirtualFile findProjectFile(@Nullable AnActionEvent e) {
    if (e != null) {
      final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
      if (file != null && file.exists()) {
        // We have a selection. Check if it is within a plugin.
        final Project project = e.getProject();
        assert (project != null);

        // Return null if this is an ios folder.
        if (FlutterExternalIdeActionGroup.isWithinIOsDirectory(file, project)) {
          return null;
        }

        final VirtualFile projectDir = project.getBaseDir();
        for (PubRoot root : PubRoots.forProject(project)) {
          if (root.isFlutterPlugin()) {
            VirtualFile rootFile = root.getRoot();
            VirtualFile aFile = file;
            while (aFile != null) {
              if (aFile.equals(rootFile)) {
                // We know a plugin resource is selected. Find the example app for it.
                for (VirtualFile child : rootFile.getChildren()) {
                  if (isExampleWithAndroidWithApp(child)) {
                    return child.findChild("android");
                  }
                }
              }
              if (aFile.equals(projectDir)) {
                aFile = null;
              }
              else {
                aFile = aFile.getParent();
              }
            }
          }
        }
        if (isProjectFileName(file.getName())) {
          return getProjectForFile(file);
        }
      }

      final Project project = e.getProject();
      if (project != null) {
        return getProjectForFile(findStudioProjectFile(project));
      }
    }
    return null;
  }

  private static void openFileInStudio(@NotNull VirtualFile projectFile, @NotNull String androidStudioPath, @Nullable String sourceFile) {
    try {
      final GeneralCommandLine cmd;
      if (SystemInfo.isMac) {
        cmd = new GeneralCommandLine().withExePath("open").withParameters("-a", androidStudioPath, "--args", projectFile.getPath());
        if (sourceFile != null) {
          cmd.addParameter(sourceFile);
        }
      }
      else {
        // TODO Open editor on sourceFile for Linux, Windows
        if (SystemInfo.isWindows) {
          androidStudioPath += "\\bin\\studio.bat";
        }
        cmd = new GeneralCommandLine().withExePath(androidStudioPath).withParameters(projectFile.getPath());
      }
      final OSProcessHandler handler = new OSProcessHandler(cmd);
      handler.addProcessListener(new ProcessAdapter() {
        @Override
        public void processTerminated(@NotNull final ProcessEvent event) {
          if (event.getExitCode() != 0) {
            FlutterMessages.showError("Error Opening", projectFile.getPath());
          }
        }
      });
      handler.startNotify();
    }
    catch (ExecutionException ex) {
      FlutterMessages.showError(
        "Error Opening",
        "Exception: " + ex.getMessage());
    }
  }

  @Nullable
  private static VirtualFile findStudioProjectFile(@NotNull Project project) {
    for (PubRoot root : PubRoots.forProject(project)) {
      for (VirtualFile child : root.getRoot().getChildren()) {
        if (isProjectFileName(child.getName())) {
          return child;
        }
        if (FlutterExternalIdeActionGroup.isAndroidDirectory(child)) {
          for (VirtualFile androidChild : child.getChildren()) {
            if (isProjectFileName(androidChild.getName())) {
              return androidChild;
            }
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private static String findAndroidStudio(@Nullable Project project) {
    if (project == null) {
      return null;
    }

    final FlutterSdk flutterSdk = FlutterSdk.getFlutterSdk(project);
    if (flutterSdk != null) {
      String androidSdkLocation = flutterSdk.queryFlutterConfig("android-studio-dir", false);
      if (androidSdkLocation != null) {
        if (androidSdkLocation.contains("/Android Studio 2")) {
          Messages.showErrorDialog(FlutterBundle.message("old.android.studio.message", File.separator),
                                   FlutterBundle.message("old.android.studio.title"));
          return null;
        }

        if (androidSdkLocation.endsWith("/")) {
          androidSdkLocation = androidSdkLocation.substring(0, androidSdkLocation.length() - 1);
        }

        // On a mac, trim off "/Contents".
        final String contents = "/Contents";
        if (SystemInfo.isMac && androidSdkLocation.endsWith(contents)) {
          return androidSdkLocation.substring(0, androidSdkLocation.length() - contents.length());
        }
        return androidSdkLocation;
      }
    }
    return null;
  }

  @Nullable
  private static VirtualFile getProjectForFile(@Nullable VirtualFile file) {
    // Expect true: isProjectFileName(file.getName()), but some flexibility is allowed.
    if (file == null) {
      return null;
    }
    if (file.isDirectory()) {
      return isAndroidWithApp(file) ? file : null;
    }
    VirtualFile dir = file.getParent();
    if (isAndroidWithApp(dir)) {
      // In case someone moves the .iml file, or the project organization gets rationalized.
      return dir;
    }
    VirtualFile project = dir.findChild("android");
    if (project != null && isAndroidWithApp(project)) {
      return project;
    }
    project = dir.findChild(".android");
    if (project != null && isAndroidWithApp(project)) {
      return project;
    }
    return null;
  }

  // Return true if the directory is named android and contains either an app (for applications) or a src (for plugins) directory.
  private static boolean isAndroidWithApp(@NotNull VirtualFile file) {
    return FlutterExternalIdeActionGroup.isAndroidDirectory(file) && (file.findChild("app") != null || file.findChild("src") != null);
  }

  // Return true if the directory has the structure of a plugin example application: a pubspec.yaml and an
  // android directory with an app. The example app directory name is not specified in case it gets renamed.
  private static boolean isExampleWithAndroidWithApp(@NotNull VirtualFile file) {
    boolean hasPubspec = false;
    boolean hasAndroid = false;
    for (VirtualFile candidate : file.getChildren()) {
      if (isAndroidWithApp(candidate)) hasAndroid = true;
      if (candidate.getName().equals("pubspec.yaml")) hasPubspec = true;
      if (hasAndroid && hasPubspec) {
        return true;
      }
    }
    return false;
  }
}
