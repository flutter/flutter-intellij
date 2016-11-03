/*
 * Copyright 2016 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspections;


import com.intellij.CommonBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotifications;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.lang.dart.DartFileType;
import com.jetbrains.lang.dart.sdk.DartSdk;
import com.jetbrains.lang.dart.sdk.DartSdkGlobalLibUtil;
import com.jetbrains.lang.dart.util.PubspecYamlUtil;
import io.flutter.FlutterBundle;
import io.flutter.module.FlutterModuleType;
import io.flutter.sdk.FlutterSdkUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;


public class WrongModuleTypeNotificationProvider extends EditorNotifications.Provider<EditorNotificationPanel> implements DumbAware {
  private static final Key<EditorNotificationPanel> KEY = Key.create("Wrong module type");
  private static final String DONT_ASK_TO_CHANGE_MODULE_TYPE_KEY = "do.not.ask.to.change.module.type"; //NON-NLS
  private static final String FLUTTER_YAML_FILE = "flutter.yaml"; //NON-NLS

  private final Project myProject;

  public WrongModuleTypeNotificationProvider(@NotNull Project project) {
    myProject = project;
  }

  private static boolean hasFlutterYaml(Module module) {
    VirtualFile baseDir = module.getProject().getBaseDir();
    if (baseDir != null) {
      VirtualFile flutterYaml = baseDir.findFileByRelativePath(FLUTTER_YAML_FILE);
      return flutterYaml != null && flutterYaml.exists();
    }
    return false;
  }

  @NotNull
  private static EditorNotificationPanel createPanel(@NotNull Project project, @NotNull Module module) {
    EditorNotificationPanel panel = new EditorNotificationPanel();
    panel.setText(FlutterBundle.message("not.flutter.bundle", module.getName()));
    panel.createActionLabel(FlutterBundle.message("change.module.type.to.flutter.and.reload.project"), () -> {
      int message = Messages.showOkCancelDialog(project, FlutterBundle.message("updating.module.type.requires.project.reload.proceed"),
                                                FlutterBundle.message("update.module.type"),
                                                FlutterBundle.message("reload.project"), CommonBundle.getCancelButtonText(), null);
      if (message == Messages.YES) {
        module.setOption(Module.ELEMENT_TYPE, FlutterModuleType.getInstance().getId());

        if (DartSdk.getDartSdk(project) != null && !DartSdkGlobalLibUtil.isDartSdkEnabled(module)) {
          ApplicationManager.getApplication().runWriteAction(() -> DartSdkGlobalLibUtil.enableDartSdk(module));
        }

        project.save();

        EditorNotifications.getInstance(project).updateAllNotifications();
        ProjectManager.getInstance().reloadProject(project);
      }
    });
    panel.createActionLabel(FlutterBundle.message("don.t.show.again.for.this.module"), () -> {
      Set<String> ignoredModules = getIgnoredModules(project);
      ignoredModules.add(module.getName());
      PropertiesComponent.getInstance(project).setValue(DONT_ASK_TO_CHANGE_MODULE_TYPE_KEY, StringUtil.join(ignoredModules, ","));
      EditorNotifications.getInstance(project).updateAllNotifications();
    });
    return panel;
  }

  @NotNull
  private static Set<String> getIgnoredModules(@NotNull Project project) {
    String value = PropertiesComponent.getInstance(project).getValue(DONT_ASK_TO_CHANGE_MODULE_TYPE_KEY, "");
    return ContainerUtil.newLinkedHashSet(StringUtil.split(value, ","));
  }

  private static boolean isFlutteryFile(@NotNull VirtualFile file) {
    String fileName = file.getName();
    return file.getFileType() == DartFileType.INSTANCE ||
           fileName.equals(FLUTTER_YAML_FILE) ||
           fileName.equals(PubspecYamlUtil.PUBSPEC_YAML);
  }

  @NotNull
  @Override
  public Key<EditorNotificationPanel> getKey() {
    return KEY;
  }

  @Override
  public EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
    if (!isFlutteryFile(file)) return null;
    Module module = ModuleUtilCore.findModuleForFile(file, myProject);
    if (module == null || FlutterSdkUtil.isFlutterModule(module) || getIgnoredModules(myProject).contains(module.getName())) return null;
    return hasFlutterYaml(module) ? createPanel(myProject, module) : null;
  }
}
