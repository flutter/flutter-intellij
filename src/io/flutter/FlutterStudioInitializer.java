package io.flutter;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class FlutterStudioInitializer {
  private static final Logger LOG = Logger.getInstance(FlutterStudioInitializer.class.getName());

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
    try {
      @SuppressWarnings("unchecked")
      Class<AnAction> clazz = (Class<AnAction>)Class.forName("com.intellij.ide.actions.NewProjectAction");
      AnAction newProject = clazz.newInstance();
      Presentation present = newProject.getTemplatePresentation();
      present.setText("New &Project...", true);
      present.setDescription("Create a new project from scratch");
      // TODO(messick): Design a New Project wizard for Android Studio + Flutter.
      replaceAction("NewProject", newProject);
    }
    catch (ClassNotFoundException ex) {
      // WebStorm doesn't have the class.
    }
    catch (IllegalAccessException | InstantiationException e) {
      LOG.error(e);
    }
  }
}
