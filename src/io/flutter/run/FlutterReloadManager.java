/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.ide.actions.SaveAllAction;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.LightweightHint;
import com.jetbrains.lang.dart.DartPluginCapabilities;
import icons.FlutterIcons;
import io.flutter.actions.FlutterAppAction;
import io.flutter.actions.ReloadFlutterApp;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.run.daemon.RunMode;
import io.flutter.settings.FlutterSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Handle the mechanics of performing a hot reload on file save.
 */
public class FlutterReloadManager {
  private final @NotNull Project myProject;
  private final FlutterSettings mySettings;

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
    this.mySettings = FlutterSettings.getInstance(myProject);

    ActionManagerEx.getInstanceEx().addAnActionListener(new AnActionListener.Adapter() {
      boolean hadUnsavedDocuments;

      @Override
      public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        if (action instanceof SaveAllAction) {
          hadUnsavedDocuments = FileDocumentManager.getInstance().getUnsavedDocuments().length > 0;
        }
      }

      @Override
      public void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        if (action instanceof SaveAllAction) {
          handleSaveAllNotification(hadUnsavedDocuments, event);
        }
      }
    }, project);
  }

  private void handleSaveAllNotification(boolean hadUnsavedDocuments, AnActionEvent event) {
    if (!mySettings.isReloadOnSave()) {
      return;
    }

    final AnAction reloadAction = ActionManager.getInstance().getAction(ReloadFlutterApp.ID);
    final FlutterApp app = getApp(reloadAction);
    if (app == null) {
      return;
    }

    if (!app.isStarted() || app.isReloading()) {
      return;
    }

    @Nullable final Editor editor = CommonDataKeys.EDITOR.getData(event.getDataContext());
    if (editor != null) {
      // Check for syntax errors in the file - we'll reload after they fix and save.
      if (documentHasErrors(editor.getDocument())) {
        return;
      }
    }

    // Reload if there are any unsaved files, or if there were any changed project
    // files since the last reload.
    if (hadUnsavedDocuments || app.hasChangesSinceLastReload()) {
      if (editor != null) {
        showNotification(editor, "Performing hot reload…", false);
      }

      app.performHotReload(supportsPauseAfterReload()).thenAccept(result -> {
        if (result.ok()) {
          return;
        }

        if (editor != null) {
          showNotification(editor, result.getMessage(), true);
        }
        else {
          // TODO(devoncarew): Improve flutter_tools reload error messages.
          showRunNotificationError(app, "Hot Reload", result.getMessage());
        }
      });
    }
  }

  public void saveAllAndReload(@NotNull FlutterApp app) {
    if (app.isStarted()) {
      FileDocumentManager.getInstance().saveAllDocuments();
      app.performHotReload(supportsPauseAfterReload()).thenAccept(result -> {
        if (!result.ok()) {
          showRunNotificationError(app, "Hot Reload", result.getMessage());
        }
      });
    }
  }

  public void saveAllAndRestart(@NotNull FlutterApp app) {
    if (app.isStarted()) {
      FileDocumentManager.getInstance().saveAllDocuments();
      app.performRestartApp().thenAccept(result -> {
        if (!result.ok()) {
          showRunNotificationError(app, "Full Restart", result.getMessage());
        }
      });
    }
  }

  private FlutterApp getApp(AnAction reloadAction) {
    if (reloadAction instanceof FlutterAppAction) {
      return ((FlutterAppAction)reloadAction).getApp();
    }

    return null;
  }

  private void showRunNotificationError(FlutterApp app, String title, String message) {
    final String toolWindowId = app.getMode() == RunMode.RUN ? ToolWindowId.RUN : ToolWindowId.DEBUG;
    final NotificationGroup notificationGroup =
      NotificationGroup.toolWindowGroup(FlutterRunNotifications.GROUP_DISPLAY_ID, toolWindowId, false);

    final Notification notification = notificationGroup.createNotification(title, message, NotificationType.ERROR, null);
    notification.setIcon(FlutterIcons.Flutter);
    notification.notify(myProject);
  }

  private FlutterApp getApp() {
    final AnAction action = ActionManager.getInstance().getAction(ReloadFlutterApp.ID);
    return action instanceof FlutterAppAction ? ((FlutterAppAction)action).getApp() : null;
  }

  private boolean supportsPauseAfterReload() {
    return DartPluginCapabilities.isSupported("supports.pausePostRequest");
  }

  // TODO(devoncarew): This technique still allows some syntax errors through; can we
  // improve the IntelliJ grammer? Are there other issues in the AST we should look for?
  private boolean documentHasErrors(@NotNull Document document) {
    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    final PsiErrorElement firstError = PsiTreeUtil.findChildOfType(psiFile, PsiErrorElement.class, false);
    return firstError != null;
  }

  private void showNotification(@NotNull Editor editor, String message, boolean isError) {
    ApplicationManager.getApplication().invokeLater(() -> {
      final JComponent component = isError
                                   ? HintUtil.createErrorLabel(message)
                                   : HintUtil.createInformationLabel(message);
      final LightweightHint hint = new LightweightHint(component);
      HintManagerImpl.getInstanceImpl().showEditorHint(
        hint, editor, HintManager.UNDER,
        HintManager.HIDE_BY_ANY_KEY | HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING | HintManager.HIDE_BY_OTHER_HINT,
        isError ? 0 : 3000, false);
    }, ModalityState.NON_MODAL, o -> editor.isDisposed() || !editor.getComponent().isShowing());
  }
}
