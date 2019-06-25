/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.actions.SaveAllAction;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectAndLibrariesScope;
import com.intellij.ui.LightweightHint;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.analyzer.DartServerData;
import com.jetbrains.lang.dart.ide.errorTreeView.DartProblemsView;
import icons.FlutterIcons;
import io.flutter.FlutterConstants;
import io.flutter.FlutterUtils;
import io.flutter.actions.FlutterAppAction;
import io.flutter.actions.ProjectActions;
import io.flutter.actions.ReloadFlutterApp;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRoots;
import io.flutter.run.common.RunMode;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.settings.FlutterSettings;
import org.dartlang.analysis.server.protocol.AnalysisErrorSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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
  private final FlutterSettings mySettings;

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
    this.mySettings = FlutterSettings.getInstance();

    ActionManagerEx.getInstanceEx().addAnActionListener(new AnActionListener.Adapter() {
      private @Nullable Project eventProject;
      private @Nullable Editor eventEditor;

      public void beforeActionPerformed(@NotNull AnAction action, @NotNull DataContext dataContext, AnActionEvent event) {
        if (action instanceof SaveAllAction) {
          // Save the current project value here. If we access later (in afterActionPerformed), we can get
          // exceptions if another event occurs before afterActionPerformed is called.
          try {
            eventProject = event.getProject();
            eventEditor = CommonDataKeys.EDITOR.getData(event.getDataContext());
          }
          catch (Throwable t) {
            // A catch-all, so any exceptions don't bubble through to the users.
            LOG.info(t);
          }
        }
        else {
          eventProject = null;
          eventEditor = null;
        }
      }

      @Override
      public void afterActionPerformed(AnAction action, @NotNull DataContext dataContext, AnActionEvent event) {
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
          FlutterUtils.warn(LOG, t);
        }
      }
    }, project);
  }

  private final AtomicBoolean handlingSave = new AtomicBoolean(false);

  private void handleSaveAllNotification(@Nullable Editor editor) {
    if (!mySettings.isReloadOnSave() || editor == null) {
      return;
    }

    if (handlingSave.get()) {
      return;
    }

    final AnAction reloadAction = ProjectActions.getAction(myProject, ReloadFlutterApp.ID);
    final FlutterApp app = getApp(reloadAction);
    if (app == null) {
      return;
    }

    if (!app.isStarted() || app.isReloading()) {
      return;
    }

    if (!app.getLaunchMode().supportsReload() || !app.appSupportsHotReload()) {
      return;
    }

    // Add an arbitrary 125ms delay to allow analysis to catch up. This delay gives the analysis server a
    // small pause to return error results in the (relatively infrequent) case where the user makes a bad
    // edit and immediately hits save.
    final int reloadDelayMs = 125;

    handlingSave.set(true);

    JobScheduler.getScheduler().schedule(() -> {
      clearLastNotification();

      if (app.getState() == FlutterApp.State.TERMINATING || app.getState() == FlutterApp.State.TERMINATED) {
        handlingSave.set(false);

        return;
      }

      // If the analysis server detects any errors in the project, it will not perform a hot reload.
      // This can cause hot reload to stop working needlessly when, eg, there is an analysis error in a test file.
      // The reloadWithError option in settings is a workaround.
      // See https://github.com/flutter/flutter/issues/27618 for the high-level goal.
      if (hasErrors(app.getProject(), app.getModule(), editor.getDocument()) && !mySettings.isReloadWithError()) {
        handlingSave.set(false);

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
      }).whenComplete((aVoid, throwable) -> handlingSave.set(false));
    }, reloadDelayMs, TimeUnit.MILLISECONDS);
  }

  private void reloadApp(@NotNull FlutterApp app, @NotNull String reason) {
    if (app.isStarted()) {
      app.performHotReload(true, reason).thenAccept(result -> {
        if (!result.ok()) {
          showRunNotification(app, "Hot Reload", result.getMessage(), true);
        }
      }).exceptionally(throwable -> {
        showRunNotification(app, "Hot Reload", throwable.getMessage(), true);
        return null;
      });
    }
  }

  public void saveAllAndReload(@NotNull FlutterApp app, @NotNull String reason) {
    FileDocumentManager.getInstance().saveAllDocuments();
    reloadApp(app, reason);
  }

  public void saveAllAndReloadAll(@NotNull List<FlutterApp> appsToReload, @NotNull String reason) {
    FileDocumentManager.getInstance().saveAllDocuments();

    for (FlutterApp app : appsToReload) {
      reloadApp(app, reason);
    }
  }

  private void restartApp(@NotNull FlutterApp app, @NotNull String reason) {
    if (app.isStarted()) {
      app.performRestartApp(reason).thenAccept(result -> {
        if (!result.ok()) {
          showRunNotification(app, "Hot Restart", result.getMessage(), true);
        }
      }).exceptionally(throwable -> {
        showRunNotification(app, "Hot Restart", throwable.getMessage(), true);
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
    restartApp(app, reason);
  }

  public void saveAllAndRestartAll(@NotNull List<FlutterApp> appsToRestart, @NotNull String reason) {
    FileDocumentManager.getInstance().saveAllDocuments();

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
    if (!ToolWindowManager.getInstance(myProject).getToolWindow(DartProblemsView.TOOLWINDOW_ID).isVisible()) {
      content += " (<a href='open.analysis.view'>view issues</a>)";
    }

    final NotificationGroup notificationGroup = getNotificationGroup(DartProblemsView.TOOLWINDOW_ID);
    final Notification notification =
      notificationGroup.createNotification(
        title, content, isError ? NotificationType.ERROR : NotificationType.INFORMATION,
        new NotificationListener.Adapter() {
          @Override
          protected void hyperlinkActivated(@NotNull final Notification notification, @NotNull final HyperlinkEvent e) {
            notification.expire();
            ToolWindowManager.getInstance(myProject).getToolWindow(DartProblemsView.TOOLWINDOW_ID).activate(null);
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

  private boolean hasErrors(@NotNull Project project, @Nullable Module module, @NotNull Document document) {
    final DartAnalysisServerService analysisServerService = DartAnalysisServerService.getInstance(project);
    final GlobalSearchScope scope = module == null ? new ProjectAndLibrariesScope(project) : module.getModuleContentScope();
    List<DartServerData.DartError> errors = analysisServerService.getErrors(scope);
    errors = errors.stream().filter(error -> shouldBlockReload(error, project, module)).collect(Collectors.toList());
    return !errors.isEmpty();
  }

  private static boolean shouldBlockReload(@NotNull DartServerData.DartError error, @NotNull Project project, @Nullable Module module) {
    // Only block on errors.
    if (!error.getSeverity().equals(AnalysisErrorSeverity.ERROR)) return false;

    final File file = new File(error.getAnalysisErrorFileSD());
    final VirtualFile virtualFile = VfsUtil.findFileByIoFile(file, false);
    if (virtualFile != null) {
      final List<PubRoot> roots = module == null ? PubRoots.forProject(project) : PubRoots.forModule(module);
      for (PubRoot root : roots) {
        // Skip errors in test files.
        final String relativePath = root.getRelativePath(virtualFile);
        if (relativePath != null && relativePath.startsWith("test/")) return false;
      }
    }

    return true;
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
