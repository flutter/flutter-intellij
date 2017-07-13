package io.flutter;

import com.intellij.ide.actions.NewProjectAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class FlutterStudioInitializer {

  public static void initializeAndroidStudio(@NotNull Project project) {
    try {
      Class.forName("com.android.tools.idea.startup.AndroidStudioInitializer");
      runActivity(project);
    }
    catch (ClassNotFoundException e) {
      // Do not try initialize it.
    }
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

  public static void runActivity(@NotNull Project project) {
    NewProjectAction newProject = new NewProjectAction();
    newProject.getTemplatePresentation().setText("New &Project...", true);
    newProject.getTemplatePresentation().setDescription("Create a new project from scratch");
    // TODO(messick): Design a New Project wizard for Android Studio + Flutter.
    replaceAction("NewProject", newProject);
  }
}
