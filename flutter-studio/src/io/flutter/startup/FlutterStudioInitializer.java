package io.flutter.startup;

import com.intellij.ide.actions.NewProjectAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.projectImport.ProjectOpenProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class FlutterStudioInitializer implements StartupActivity {

  @Override
  public void runActivity(@NotNull Project project) {
    NewProjectAction newProject = new NewProjectAction();
    newProject.getTemplatePresentation().setText("New &Project...", true);
    newProject.getTemplatePresentation().setDescription("Create a new project from scratch");
    // TODO(messick): Design a New Project wizard for Android Studio + Flutter.
    replaceAction("NewProject", newProject);
  }

  public static void replaceAction(@NotNull String actionId, @NotNull AnAction newAction) {
    ActionManager actionManager = ActionManager.getInstance();
    AnAction oldAction = actionManager.getAction(actionId);
    if (oldAction != null) {
      newAction.getTemplatePresentation().setIcon(oldAction.getTemplatePresentation().getIcon());
      actionManager.unregisterAction(actionId);
    }
    actionManager.registerAction(actionId, newAction);
  }
}
