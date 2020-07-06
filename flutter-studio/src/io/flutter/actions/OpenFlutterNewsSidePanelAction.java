/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.flutter.actions;

import com.android.tools.idea.assistant.AssistantBundleCreator;
import com.android.tools.idea.assistant.OpenAssistSidePanelAction;
import com.intellij.ide.actions.WhatsNewAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import io.flutter.FlutterInitializer;
import io.flutter.assistant.whatsnew.whatsnew.FlutterNewsBundleCreator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

// Adapted from com.android.tools.idea.whatsnew.assistant.WhatsNewSidePanelAction.
public class OpenFlutterNewsSidePanelAction extends OpenAssistSidePanelAction {
  @NotNull
  private static final WhatsNewAction action = new WhatsNewAction();
  @NotNull
  private static final Set<Project> openProjectTools = new HashSet<>();

  @NotNull
  private final Map<Project, FlutterNewsToolWindowListener> myProjectToListenerMap;

  public OpenFlutterNewsSidePanelAction() {
    myProjectToListenerMap = new HashMap<>();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    // Project being null can happen when Studio first starts and doesn't have window focus
    Presentation presentation = e.getPresentation();
    if (e.getProject() == null) {
      presentation.setEnabled(false);
    }
    else if (!presentation.isEnabled()) {
      presentation.setEnabled(true);
    }

    action.update(e);
    presentation.setText("What's New in Flutter");
    presentation.setDescription("See the recent updates to Flutter and the plugin.");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    openWhatsNewSidePanel(Objects.requireNonNull(event.getProject()), false);
  }

  public void openWhatsNewSidePanel(@NotNull Project project, boolean isAutoOpened) {
    FlutterNewsBundleCreator bundleCreator = AssistantBundleCreator.EP_NAME.findExtension(FlutterNewsBundleCreator.class);
    if (bundleCreator == null) {
      return;
    }

    FlutterNewsToolWindowListener.fireOpenEvent(project, isAutoOpened);
    openWindow(FlutterNewsBundleCreator.BUNDLE_ID, project);

    // Only register a new listener if there isn't already one, to avoid multiple OPEN/CLOSE events
    myProjectToListenerMap.computeIfAbsent(project, this::newFlutterNewsToolWindowListener);
  }

  @NotNull
  private OpenFlutterNewsSidePanelAction.FlutterNewsToolWindowListener newFlutterNewsToolWindowListener(@NotNull Project project) {
    FlutterNewsToolWindowListener listener = new FlutterNewsToolWindowListener(project, myProjectToListenerMap);
    project.getMessageBus().connect(project).subscribe(ToolWindowManagerListener.TOPIC, listener);
    return listener;
  }

  static class FlutterNewsToolWindowListener implements ToolWindowManagerListener {
    @NotNull private final Project myProject;
    @NotNull Map<Project, FlutterNewsToolWindowListener> myProjectToListenerMap;
    private boolean isOpen;

    private FlutterNewsToolWindowListener(@NotNull Project project,
                                          @NotNull Map<Project, FlutterNewsToolWindowListener> projectToListenerMap) {
      myProject = project;
      myProjectToListenerMap = projectToListenerMap;
      isOpen = true; // Start off as opened so we don't fire an extra opened event

      // Need an additional listener for project close, because the below invokeLater isn't fired in time before closing
      project.getMessageBus().connect(project).subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
        @Override
        public void projectClosed(@NotNull Project project) {
          if (!project.equals(myProject)) {
            return;
          }
          if (isOpen) {
            fireClosedEvent(myProject);
            isOpen = false;
          }
          myProjectToListenerMap.remove(project);
        }
      });
    }

    @Override
    public void toolWindowRegistered(@NotNull String id) {
    }

    @Override
    public void toolWindowUnregistered(@NotNull String id, @NotNull ToolWindow toolWindow) {
      if (id.equals(OpenAssistSidePanelAction.TOOL_WINDOW_TITLE)) {
        myProjectToListenerMap.remove(myProject);
      }
    }

    /**
     * Fire metrics and update the actual state after a state change is received.
     * The logic is wrapped in invokeLater because dragging and dropping the StripeButton temporarily
     * hides and then shows the window. Otherwise, the handler would think the window was closed,
     * even though it was only dragged.
     */
    @Override
    public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (myProject.isDisposed()) {
          myProjectToListenerMap.remove(myProject);
          return;
        }

        ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(OpenAssistSidePanelAction.TOOL_WINDOW_TITLE);
        if (window == null) {
          return;
        }
        if (!FlutterNewsBundleCreator.BUNDLE_ID.equals(window.getHelpId())) {
          return;
        }
        if (isOpen && !window.isVisible()) {
          fireClosedEvent(myProject);
          isOpen = false;
        }
        else if (!isOpen && window.isVisible()) {
          fireOpenEvent(myProject, false);
          isOpen = true;
        }
      });
    }

    private static void fireOpenEvent(@NotNull Project project, boolean isAutoOpened) {
      // An extra "open" can fire when the window is already open and the user manually uses the OpenFlutterNewsSidePanelAction
      // again, so in this case just ignore the call.
      if (openProjectTools.contains(project)) return;
      FlutterInitializer.getAnalytics().sendEvent("intellij", isAutoOpened ? "AutoOpenFlutterNews" : "OpenFlutterNews");
    }

    private static void fireClosedEvent(@NotNull Project project) {
      openProjectTools.remove(project);
      FlutterInitializer.getAnalytics().sendEvent("intellij", "CloseFlutterNews");
    }
  }
}
