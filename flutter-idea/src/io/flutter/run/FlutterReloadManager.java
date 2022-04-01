/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.AppTopics;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.concurrency.JobScheduler;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.ide.actions.SaveAllAction;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.LightweightHint;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.lang.dart.ide.errorTreeView.DartProblemsView;
import com.jetbrains.lang.dart.psi.DartFile;
import icons.FlutterIcons;
import io.flutter.FlutterConstants;
import io.flutter.FlutterUtils;
import io.flutter.actions.FlutterAppAction;
import io.flutter.actions.ProjectActions;
import io.flutter.actions.ReloadFlutterApp;
import io.flutter.bazel.Workspace;
import io.flutter.bazel.WorkspaceCache;
import io.flutter.run.common.RunMode;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.settings.FlutterSettings;
import io.flutter.utils.FlutterModuleUtils;
import io.flutter.utils.MostlySilentColoredProcessHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Handle the mechanics of performing a hot reload on file save.
 */
public class FlutterReloadManager {
  private static final Logger LOG = Logger.getInstance(FlutterReloadManager.class);

  private static final Map<String, NotificationGroup> toolWindowNotificationGroups = new HashMap<>();

  private static NotificationGroup getNotificationGroup(String toolWindowId) {
    if (!toolWindowNotificationGroups.containsKey(toolWindowId)) {
      final NotificationGroup notificationGroup = NotificationGroup.toolWindowGroup("Flutter " + toolWindowId, toolWindowId, false);
      toolWindowNotificationGroups.put(toolWindowId, notificationGroup);
    }

    return toolWindowNotificationGroups.get(toolWindowId);
  }

  private final @NotNull Project myProject;

  private Notification lastNotification;

  /**
   * Initialize the reload manager for the given project.
   */
  public static void init(@NotNull Project project) {
    // Call getInstance() will init FlutterReloadManager for the given project.
    getInstance(project);
  }

  public static FlutterReloadManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, FlutterReloadManager.class);
  }

  private FlutterReloadManager(@NotNull Project project) {
    this.myProject = project;

    final MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(project);
    connection.subscribe(AnActionListener.TOPIC, new AnActionListener() {
      private @Nullable Project eventProject;
      private @Nullable Editor eventEditor;

      public void beforeActionPerformed(@NotNull AnAction action, @NotNull DataContext dataContext, @NotNull AnActionEvent event) {
        if (!(action instanceof SaveAllAction)) {
          return;
        }

        // Save the current project value here. If we access later (in afterActionPerformed), we can get
        // exceptions if another event occurs before afterActionPerformed is called.
        try {
          eventProject = event.getProject();
          eventEditor = CommonDataKeys.EDITOR.getData(event.getDataContext());
        }
        catch (Throwable t) {
          // A catch-all, so any exceptions don't bubble through to the users.
          LOG.warn("Exception from FlutterReloadManager", t);
        }
      }

      @Override
      public void afterActionPerformed(@NotNull AnAction action, @NotNull DataContext dataContext, @NotNull AnActionEvent event) {
        if (!(action instanceof SaveAllAction)) {
          return;
        }

        // If this save all action is not for the current project, return.
        if (myProject != eventProject) {
          return;
        }

        try {
          handleSaveAllNotification(eventEditor);
        }
        catch (Throwable t) {
          FlutterUtils.warn(LOG, "Exception from hot reload on save", t);
        }
      }
    });
    connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerListener() {
      @Override
      public void beforeAllDocumentsSaving() {
        if (!FlutterSettings.getInstance().isReloadOnSave()) return;
        if (myProject.isDisposed()) return;
        if (!FlutterModuleUtils.hasFlutterModule(myProject)) return;
        // The "Save files if the IDE is idle ..." option runs whether there are any changes or not.
        boolean isModified = false;
        for (FileEditor fileEditor : FileEditorManager.getInstance(myProject).getAllEditors()) {
          if (fileEditor.isModified()) {
            isModified = true;
            break;
          }
        }
        if (!isModified) return;

        ApplicationManager.getApplication().invokeLater(() -> {
          // Find a Dart editor to trigger the reload.
          final Editor anEditor = ApplicationManager.getApplication().runReadAction((Computable<Editor>)() -> {
            Editor someEditor = null;
            for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
              if (editor.isDisposed()) continue;
              if (editor.getProject() != myProject) continue;
              final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getDocument());
              if (psiFile instanceof DartFile && someEditor == null) {
                someEditor = editor;
              }
              if (null != PsiTreeUtil.findChildOfType(psiFile, PsiErrorElement.class, false)) {
                // If there are analysis errors we want to silently exit, without showing a notification.
                return null;
              }
            }
            return someEditor;
          });
          handleSaveAllNotification(anEditor);
        }, ModalityState.any());
      }
    });
  }

  private void handleSaveAllNotification(@Nullable Editor editor) {
    if (!FlutterSettings.getInstance().isReloadOnSave() || editor == null) {
      return;
    }

    @NotNull Path configPath = PathManager.getConfigDir();
    @Nullable VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
    if (file == null) {
      return;
    }
    if (file.getPath().startsWith(configPath.toString())) {
      return; // Ignore changes to scratch files.
    }
    if (System.currentTimeMillis() - file.getTimeStamp() > 500) {
      // If the file was saved in the last half-second, assume it should trigger hot reload
      // because it was probably just saved before this notification was generated.
      // Files saved a long time ago should not trigger hot reload.
      return;
    }

    final AnAction reloadAction = ProjectActions.getAction(myProject, ReloadFlutterApp.ID);
    final FlutterApp app = getApp(reloadAction);
    if (app == null) {
      return;
    }

    if (!app.getLaunchMode().supportsReload() || !app.appSupportsHotReload()) {
      return;
    }

    if (!app.isStarted() || app.isReloading()) {
      return;
    }

    // Transition the app to an about-to-reload state.
    final FlutterApp.State previousAppState = app.transitionStartingHotReload();

    JobScheduler.getScheduler().schedule(() -> {
      if (WorkspaceCache.getInstance(myProject).isBazel()) {
        syncFiles();
      }

      clearLastNotification();

      if (!app.isConnected()) {
        return;
      }

      // Don't reload if we find structural errors with the current file.
      if (hasErrorsInFile(editor.getDocument())) {
        app.cancelHotReloadState(previousAppState);
        showAnalysisNotification("Reload not performed", "Analysis issues found", true);

        return;
      }

      final Notification notification = showRunNotification(app, null, "Reloading…", false);
      final long startTime = System.currentTimeMillis();

      app.performHotReload(true, FlutterConstants.RELOAD_REASON_SAVE).thenAccept(result -> {
        if (!result.ok()) {
          notification.expire();
          showRunNotification(app, "Hot Reload Error", result.getMessage(), true);
        }
        else {
          // Make sure the reloading message is displayed for at least 2 seconds (so it doesn't just flash by).
          final long delay = Math.max(0, 2000 - (System.currentTimeMillis() - startTime));

          JobScheduler.getScheduler().schedule(() -> UIUtil.invokeLaterIfNeeded(() -> {
            notification.expire();

            // If the 'Reloading…' notification is still the most recent one, then clear it.
            if (isLastNotification(notification)) {
              removeRunNotifications(app);
            }
          }), delay, TimeUnit.MILLISECONDS);
        }
      });
    }, 0, TimeUnit.MILLISECONDS);
  }

  private void syncFiles() {
    final Workspace workspace = WorkspaceCache.getInstance(myProject).get();
    assert workspace != null;

    final String script = workspace.getRoot().getPath() + "/" + workspace.getSyncScript();
    final GeneralCommandLine commandLine = new GeneralCommandLine().withWorkDirectory(workspace.getRoot().getPath());
    commandLine.setCharset(StandardCharsets.UTF_8);
    commandLine.setExePath(FileUtil.toSystemDependentName(script));

    try {
      final MostlySilentColoredProcessHandler handler = new MostlySilentColoredProcessHandler(commandLine);
      handler.startNotify();
      if (!handler.getProcess().waitFor(10, TimeUnit.SECONDS)) {
        LOG.error("Syncing files timed out");
      }
    }
    catch (ExecutionException | InterruptedException e) {
      LOG.error("Unable to sync files: " + e);
    }
  }

  private void reloadApp(@NotNull FlutterApp app, @NotNull String reason) {
    if (app.isStarted()) {
      app.performHotReload(true, reason).thenAccept(result -> {
        if (!result.ok()) {
          showRunNotification(app, "Hot Reload Error", result.getMessage(), true);
        }
      }).exceptionally(throwable -> {
        showRunNotification(app, "Hot Reload Error", throwable.getMessage(), true);
        return null;
      });
    }
  }

  public void saveAllAndReload(@NotNull FlutterApp app, @NotNull String reason) {
    FileDocumentManager.getInstance().saveAllDocuments();

    clearLastNotification();

    reloadApp(app, reason);
  }

  public void saveAllAndReloadAll(@NotNull List<FlutterApp> appsToReload, @NotNull String reason) {
    FileDocumentManager.getInstance().saveAllDocuments();

    clearLastNotification();

    for (FlutterApp app : appsToReload) {
      reloadApp(app, reason);
    }
  }

  private void restartApp(@NotNull FlutterApp app, @NotNull String reason) {
    if (app.isStarted()) {
      app.performRestartApp(reason).thenAccept(result -> {
        if (!result.ok()) {
          showRunNotification(app, "Hot Restart Error", result.getMessage(), true);
        }
      }).exceptionally(throwable -> {
        showRunNotification(app, "Hot Restart Error", throwable.getMessage(), true);
        return null;
      });

      final FlutterDevice device = app.device();
      if (device != null) {
        device.bringToFront();
      }
    }
  }

  public void saveAllAndRestart(@NotNull FlutterApp app, @NotNull String reason) {
    FileDocumentManager.getInstance().saveAllDocuments();

    clearLastNotification();

    restartApp(app, reason);
  }

  public void saveAllAndRestartAll(@NotNull List<FlutterApp> appsToRestart, @NotNull String reason) {
    FileDocumentManager.getInstance().saveAllDocuments();

    clearLastNotification();

    for (FlutterApp app : appsToRestart) {
      restartApp(app, reason);
    }
  }

  @Nullable
  private FlutterApp getApp(AnAction reloadAction) {
    if (reloadAction instanceof FlutterAppAction) {
      return ((FlutterAppAction)reloadAction).getApp();
    }
    else {
      return null;
    }
  }

  private void showAnalysisNotification(@NotNull String title, @NotNull String content, boolean isError) {
    final ToolWindow dartProblemsView = ToolWindowManager.getInstance(myProject).getToolWindow(DartProblemsView.TOOLWINDOW_ID);
    if (dartProblemsView != null && !dartProblemsView.isVisible()) {
      content += " (<a href='open.analysis.view'>view issues</a>)";
    }

    final NotificationGroup notificationGroup = getNotificationGroup(DartProblemsView.TOOLWINDOW_ID);
    final Notification notification = notificationGroup.createNotification(
      title, content, isError ? NotificationType.ERROR : NotificationType.INFORMATION,
      new NotificationListener.Adapter() {
        @Override
        protected void hyperlinkActivated(@NotNull final Notification notification, @NotNull final HyperlinkEvent e) {
          notification.expire();

          final ToolWindow dartProblemsView = ToolWindowManager.getInstance(myProject).getToolWindow(DartProblemsView.TOOLWINDOW_ID);
          if (dartProblemsView != null) {
            dartProblemsView.activate(null);
          }
        }
      });
    notification.setIcon(FlutterIcons.Flutter);
    notification.notify(myProject);

    lastNotification = notification;
  }

  private Notification showRunNotification(@NotNull FlutterApp app, @Nullable String title, @NotNull String content, boolean isError) {
    final String toolWindowId = app.getMode() == RunMode.DEBUG ? ToolWindowId.DEBUG : ToolWindowId.RUN;
    final NotificationGroup notificationGroup = getNotificationGroup(toolWindowId);
    final Notification notification;
    if (title == null) {
      notification = notificationGroup.createNotification(content, isError ? NotificationType.ERROR : NotificationType.INFORMATION);
    }
    else {
      notification =
        notificationGroup.createNotification(title, content, isError ? NotificationType.ERROR : NotificationType.INFORMATION, null);
    }
    notification.setIcon(FlutterIcons.Flutter);
    notification.notify(myProject);

    lastNotification = notification;

    return notification;
  }

  private boolean isLastNotification(final Notification notification) {
    return notification == lastNotification;
  }

  private void clearLastNotification() {
    lastNotification = null;
  }

  private void removeRunNotifications(FlutterApp app) {
    final String toolWindowId = app.getMode() == RunMode.DEBUG ? ToolWindowId.DEBUG : ToolWindowId.RUN;
    final Balloon balloon = ToolWindowManager.getInstance(myProject).getToolWindowBalloon(toolWindowId);
    if (balloon != null) {
      balloon.hide();
    }
  }

  private boolean hasErrorsInFile(@NotNull Document document) {
    // We use the IntelliJ parser and look for syntax errors in the current document.
    // We block reload if we find issues in the immediate file. We don't block reload if there
    // are analysis issues in other files; the compilation errors from the flutter tool
    // will indicate to the user where the problems are.

    final PsiErrorElement firstError = ApplicationManager.getApplication().runReadAction((Computable<PsiErrorElement>)() -> {
      final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
      if (psiFile instanceof DartFile) {
        return PsiTreeUtil.findChildOfType(psiFile, PsiErrorElement.class, false);
      }
      else {
        return null;
      }
    });
    return firstError != null;
  }

  private LightweightHint showEditorHint(@NotNull Editor editor, String message, boolean isError) {
    final AtomicReference<LightweightHint> ref = new AtomicReference<>();

    ApplicationManager.getApplication().invokeAndWait(() -> {
      final JComponent component = isError
                                   ? HintUtil.createErrorLabel(message)
                                   : HintUtil.createInformationLabel(message);
      final LightweightHint hint = new LightweightHint(component);
      ref.set(hint);
      HintManagerImpl.getInstanceImpl().showEditorHint(
        hint, editor, HintManager.UNDER,
        HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING | HintManager.HIDE_BY_OTHER_HINT,
        isError ? 0 : 3000, false);
    });

    return ref.get();
  }
}
