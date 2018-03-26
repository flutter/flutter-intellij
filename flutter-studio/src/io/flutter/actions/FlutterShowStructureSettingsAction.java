/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.actions;

import com.android.tools.idea.gradle.actions.AndroidShowStructureSettingsAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import io.flutter.utils.FlutterModuleUtils;

// This restores the Project Structure editor but does not convert it to use the
// new version defined by Android Studio. That one needs a lot of work to function
// with Flutter projects.
public class FlutterShowStructureSettingsAction extends AndroidShowStructureSettingsAction {

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null && FlutterModuleUtils.hasFlutterModule(project)) {
      e.getPresentation().setEnabledAndVisible(true);
    }
    else {
      super.update(e);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    //Project project = e.getProject();
    //if (project == null && IdeInfo.getInstance().isAndroidStudio()) {
    //  project = ProjectManager.getInstance().getDefaultProject();
    //  showAndroidProjectStructure(project);
    //  return;
    //}
    //
    //if (project != null) {
    //  showAndroidProjectStructure(project);
    //  return;
    //}

    super.actionPerformed(e);
  }

  //private static void showAndroidProjectStructure(@NotNull Project project) {
  //  if (StudioFlags.NEW_PSD_ENABLED.get()) {
  //    ProjectStructureConfigurable projectStructure = ProjectStructureConfigurable.getInstance(project);
  //    AtomicBoolean needsSync = new AtomicBoolean();
  //    ProjectStructureConfigurable.ProjectStructureChangeListener changeListener = () -> needsSync.set(true);
  //    projectStructure.add(changeListener);
  //    projectStructure.showDialog();
  //    projectStructure.remove(changeListener);
  //    if (needsSync.get()) {
  //      GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(project, TRIGGER_PROJECT_MODIFIED);
  //    }
  //    return;
  //  }
  //  AndroidProjectStructureConfigurable.getInstance(project).showDialog();
  //}
}
