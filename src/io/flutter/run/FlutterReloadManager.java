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
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.actions.SaveAllAction;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.*;
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
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectAndLibrariesScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.LightweightHint;
import com.intellij.util.ReflectionUtil;
import com.jetbrains.lang.dart.DartPluginCapabilities;
import com.jetbrains.lang.dart.analyzer.DartAnalysisServerService;
import com.jetbrains.lang.dart.analyzer.DartServerData;
import com.jetbrains.lang.dart.ide.errorTreeView.DartProblemsView;
import icons.FlutterIcons;
import io.flutter.FlutterMessages;
import io.flutter.actions.FlutterAppAction;
import io.flutter.actions.OpenSimulatorAction;
import io.flutter.actions.ProjectActions;
import io.flutter.actions.ReloadFlutterApp;
import io.flutter.pub.PubRoot;
import io.flutter.pub.PubRoots;
import io.flutter.run.daemon.DeviceService;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.run.daemon.FlutterDevice;
import io.flutter.run.daemon.RunMode;
import io.flutter.settings.FlutterSettings;
import org.dartlang.analysis.server.protocol.AnalysisErrorSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Handle the mechanics of performing a hot reload on file save.
 */
public class FlutterReloadManager {
  public static final String RELOAD_ON_SAVE_FEEDBACK_URL = "https://goo.gl/Pab4Li";

  private static final Logger LOG = Logger.getInstance(FlutterReloadManager.class);

  private static final String reloadSaveFeedbackKey = "io.flutter.askedUserReloadSaveFeedback";

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
    this.mySettings = FlutterSettings.getInstance();

    ActionManagerEx.getInstanceEx().addAnActionListener(new AnActionListener.Adapter() {
      private Project eventProject;
      private Editor eventEditor;

      public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
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
      public void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
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
          LOG.error(t);
        }
      }
    }, project);

    FlutterSettings.getInstance().addListener(new FlutterSettings.Listener() {
      boolean reloadOnSave = FlutterSettings.getInstance().isReloadOnSave();

      @Override
      public void settingsChanged() {
        final boolean newReloadOnSave = FlutterSettings.getInstance().isReloadOnSave();
        if (reloadOnSave && !newReloadOnSave) {
          // The user is turning off reload on save; see if we should ask why.
          final PropertiesComponent properties = PropertiesComponent.getInstance();
          if (!properties.getBoolean(reloadSaveFeedbackKey, false)) {
            properties.setValue(reloadSaveFeedbackKey, true);

            JobScheduler.getScheduler().schedule(() -> showDisableReloadSaveNotification(), 1, TimeUnit.SECONDS);
          }
        }
        reloadOnSave = newReloadOnSave;
      }
    });
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

    if (!(editor instanceof EditorEx)) {
      return;
    }

    final EditorEx editorEx = (EditorEx)editor;
    final VirtualFile file = editorEx.getVirtualFile();

    // Add an arbitrary 125ms delay to allow analysis to catch up. This delay gives the analysis server a
    // small pause to return error results in the (relatively infrequent) case where the user makes a bad
    // edit and immediately hits save.
    final int reloadDelayMs = 125;

    handlingSave.set(true);

    JobScheduler.getScheduler().schedule(() -> {
      if (hasErrors(app.getProject(), app.getModule(), editor.getDocument())) {
        handlingSave.set(false);

        showAnalysisNotification("Reload not performed", "Analysis issues found", true);
      }
      else {
        final Notification notification = showRunNotification(app, null, "Reloading…", false);

        app.performHotReload(supportsPauseAfterReload()).thenAccept(result -> {
          notification.expire();

          if (!result.ok()) {
            showRunNotification(app, "Hot Reload Error", result.getMessage(), true);
          }
        }).whenComplete((aVoid, throwable) -> handlingSave.set(false));
      }
    }, reloadDelayMs, TimeUnit.MILLISECONDS);
  }

  public void saveAllAndReload(@NotNull FlutterApp app) {
    if (app.isStarted()) {
      FileDocumentManager.getInstance().saveAllDocuments();
      app.performHotReload(supportsPauseAfterReload()).thenAccept(result -> {
        if (!result.ok()) {
          showRunNotification(app, "Hot Reload", result.getMessage(), true);
        }
      });
    }
  }

  public void saveAllAndRestart(@NotNull FlutterApp app) {
    if (app.isStarted()) {
      FileDocumentManager.getInstance().saveAllDocuments();
      app.performRestartApp().thenAccept(result -> {
        if (!result.ok()) {
          showRunNotification(app, "Full Restart", result.getMessage(), true);
        }
      });
      // Bring iOS simulator to front.
      final FlutterDevice device = DeviceService.getInstance(myProject).getSelectedDevice();
      if (device != null && device.emulator() && device.isIOS()) {
        new OpenSimulatorAction(true).actionPerformed(null);
      }
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

    final NotificationGroup notificationGroup =
      NotificationGroup.toolWindowGroup(FlutterRunNotifications.GROUP_DISPLAY_ID, DartProblemsView.TOOLWINDOW_ID, false);
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
  }

  private Notification showRunNotification(@NotNull FlutterApp app, @Nullable String title, @NotNull String content, boolean isError) {
    final String toolWindowId = app.getMode() == RunMode.RUN ? ToolWindowId.RUN : ToolWindowId.DEBUG;
    final NotificationGroup notificationGroup =
      NotificationGroup.toolWindowGroup(FlutterRunNotifications.GROUP_DISPLAY_ID, toolWindowId, false);
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
    return notification;
  }

  private FlutterApp getApp() {
    final AnAction action = ProjectActions.getAction(myProject, ReloadFlutterApp.ID);
    return action instanceof FlutterAppAction ? ((FlutterAppAction)action).getApp() : null;
  }

  private boolean supportsPauseAfterReload() {
    return DartPluginCapabilities.isSupported("supports.pausePostRequest");
  }

  private boolean hasErrors(@NotNull Project project, @Nullable Module module, @NotNull Document document) {
    // For 2017.1, we use the IntelliJ parser and look for syntax errors in the current document.
    // For 2017.2 and later, we instead rely on the analysis server's results for files in the app's module.

    final DartAnalysisServerService analysisServerService = DartAnalysisServerService.getInstance(project);

    // TODO(devoncarew): Remove the use of reflection when our minimum revs to 2017.2.
    final Method getErrorsMethod = ReflectionUtil.getMethod(analysisServerService.getClass(), "getErrors", SearchScope.class);
    if (getErrorsMethod == null) {
      final PsiErrorElement firstError = ApplicationManager.getApplication().runReadAction((Computable<PsiErrorElement>)() -> {
        final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        return PsiTreeUtil.findChildOfType(psiFile, PsiErrorElement.class, false);
      });
      return firstError != null;
    }
    else {
      final GlobalSearchScope scope = module == null ? new ProjectAndLibrariesScope(project) : module.getModuleContentScope();
      try {
        //List<DartServerData.DartError> errors = analysisServerService.getErrors(scope);
        //noinspection unchecked
        List<DartServerData.DartError> errors = (List<DartServerData.DartError>)getErrorsMethod.invoke(analysisServerService, scope);
        errors = errors.stream().filter(error -> shouldBlockReload(error, project, module)).collect(Collectors.toList());
        return !errors.isEmpty();
      }
      catch (IllegalAccessException | InvocationTargetException e) {
        return false;
      }
    }
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

  private void showDisableReloadSaveNotification() {
    final Notification notification = new Notification(
      FlutterMessages.FLUTTER_NOTIFICATION_GOUP_ID,
      "Flutter Reload on Save",
      "Disabling reload on save; consider providing feedback on this feature to help us improve future versions.",
      NotificationType.INFORMATION);
    notification.addAction(new AnAction("Provide Feedback") {
      @Override
      public void actionPerformed(AnActionEvent event) {
        notification.expire();
        BrowserUtil.browse(RELOAD_ON_SAVE_FEEDBACK_URL);
      }
    });
    notification.addAction(new AnAction("No thanks") {
      @Override
      public void actionPerformed(AnActionEvent event) {
        notification.expire();
      }
    });
    Notifications.Bus.notify(notification);
  }
}
