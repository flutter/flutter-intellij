package io.flutter.widgetpreviewer;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class WidgetPreviewerToolWindowFactory implements ToolWindowFactory {
  public static final String TOOL_WINDOW_ID = "Flutter Widget Preview";

  @Override
  public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    WidgetPreviewerPanel widgetPreviewerPanel = new WidgetPreviewerPanel(project, toolWindow);
    ContentFactory contentFactory = ContentFactory.getInstance();
    Content content = contentFactory.createContent(widgetPreviewerPanel, "", false);
    toolWindow.getContentManager().addContent(content);
  }
}
