package io.flutter.utils;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import org.jetbrains.annotations.NotNull;

/**
 * Helps to initialize a tool window with the same visibility state as when the project was previously closed.
 */
public abstract class ViewListener implements ToolWindowManagerListener {
  private final @NotNull Project project;
  private final String toolWindowId;
  private final String toolWindowVisibleProperty;

  public ViewListener(@NotNull Project project, String toolWindowId, String toolWindowVisibleProperty) {
    this.project = project;
    this.toolWindowId = toolWindowId;
    this.toolWindowVisibleProperty = toolWindowVisibleProperty;
  }

  @Override
  public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
    ToolWindow toolWindow = toolWindowManager.getToolWindow(toolWindowId);
    // We only make tool windows available once we've found a Flutter project, so we want to avoid setting the visible property until then.
    if (toolWindow != null && toolWindow.isAvailable()) {
      PropertiesComponent.getInstance(project).setValue(toolWindowVisibleProperty, toolWindow.isVisible(), false);
    }
  }
}