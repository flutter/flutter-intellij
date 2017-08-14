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
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.LightweightHint;
import com.intellij.util.ReflectionUtil;
import com.jetbrains.lang.dart.DartPluginCapabilities;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.analyzer.DartServerData;
import com.jetbrains.lang.dart.ide.errorTreeView.DartProblemsView;
import icons.FlutterIcons;
import io.flutter.actions.FlutterAppAction;
import io.flutter.actions.ReloadFlutterApp;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.run.daemon.RunMode;
import io.flutter.settings.FlutterSettings;
import org.dartlang.analysis.server.protocol.AnalysisErrorSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Handle the mechanics of performing a hot reload on file save.
 */
public class FlutterReloadManager {
  private static final Logger LOG = Logger.getInstance(FlutterReloadManager.class.getName());

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
      @Override
      public void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        if (action instanceof SaveAllAction) {
          try {
            handleSaveAllNotification(event);
          }
          catch (Throwable t) {
            LOG.error(t);
          }
        }
      }
    }, project);
  }

  private void handleSaveAllNotification(AnActionEvent event) {
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
    if (editor == null) {
      return;
    }

    if (!(editor instanceof EditorEx)) {
      return;
    }

    final EditorEx editorEx = (EditorEx)editor;
    final VirtualFile file = editorEx.getVirtualFile();
    final Project project = editor.getProject();
    if (file == null || project == null) {
      return;
    }
    final Module module = ModuleUtil.findModuleForFile(file, project);
    if (module == null) {
      return;
    }

    // Only reload if it's in the same module.
    if (!app.isSameModule(module)) {
      return;
    }

    if (hasErrors(project, module, editor.getDocument())) {
      showNotification(editor, "Reload not performed", true);
      showAnalysisNotification("Analysis issues found");
    }
    else {
      final LightweightHint hint = showNotification(editor, "Reloading…", false);

      app.performHotReload(supportsPauseAfterReload()).thenAccept(result -> {
        hint.hide();

        if (!result.ok()) {
          showNotification(editor, result.getMessage(), true);
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

  private void showAnalysisNotification(String message) {
    final NotificationGroup notificationGroup =
      NotificationGroup.toolWindowGroup(FlutterRunNotifications.GROUP_DISPLAY_ID, DartProblemsView.TOOLWINDOW_ID, false);
    final Notification notification = notificationGroup.createNotification(message, NotificationType.ERROR);
    notification.setIcon(FlutterIcons.Flutter);
    notification.notify(myProject);
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

  private boolean hasErrors(@NotNull Project project, @NotNull Module module, @NotNull Document document) {
    // For 2017.1, we use the IntelliJ parser and look for syntax errors in the current document.
    // For 2017.2 and later, we instead rely on the analysis server's results for files in the app's module.

    final DartAnalysisServerService analysisServerService = DartAnalysisServerService.getInstance(project);

    // TODO(devoncarew): Remove the use of reflection when our minimum revs to 2017.2.
    final Method getErrorsMethod = ReflectionUtil.getMethod(analysisServerService.getClass(), "getErrors", SearchScope.class);
    if (getErrorsMethod == null) {
      final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
      final PsiErrorElement firstError = PsiTreeUtil.findChildOfType(psiFile, PsiErrorElement.class, false);
      return firstError != null;
    }
    else {
      final GlobalSearchScope scope = module.getModuleContentScope();
      try {
        //List<DartServerData.DartError> errors = analysisServerService.getErrors(scope);
        //noinspection unchecked
        List<DartServerData.DartError> errors = (List<DartServerData.DartError>)getErrorsMethod.invoke(analysisServerService, scope);
        errors = errors.stream().filter(error -> error.getSeverity().equals(AnalysisErrorSeverity.ERROR)).collect(Collectors.toList());
        return !errors.isEmpty();
      }
      catch (IllegalAccessException | InvocationTargetException e) {
        return false;
      }
    }
  }

  private LightweightHint showNotification(@NotNull Editor editor, String message, boolean isError) {
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
