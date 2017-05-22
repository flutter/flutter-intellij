/*
 * Copyright 2017 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.run;

import com.intellij.ide.actions.SaveAllAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.lang.dart.DartPluginCapabilities;
import io.flutter.actions.FlutterAppAction;
import io.flutter.actions.ReloadFlutterApp;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.settings.FlutterSettings;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

// TODO: Also experiment with live reloading:
//   - if the analysis server completes an analysis w/o issues, then issue a reload

// TODO: Show a live typing / live reloading butter bar in the editor?

// TODO: investigate com.intellij.openapi.actionSystem.ex.AnActionListener

/**
 * Handle the mechanics of performing a hot reload on file save.
 */
public class FlutterReloadManager /*extends FileDocumentManagerAdapter*/ {
  private final @NotNull Project myProject;
  private final FlutterSettings mySettings;

  private int ignoreCount = 0;

  private Timer timer;

  public static FlutterReloadManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, FlutterReloadManager.class);
  }

  private FlutterReloadManager(@NotNull Project project) {
    this.myProject = project;
    this.mySettings = FlutterSettings.getInstance(myProject);

    // Subscribe to file saved notifications.
    //project.getMessageBus().connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, this);
    // TODO: ??
    // public void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
    // Save All (Save all files and settings)
    ActionManagerEx.getInstanceEx().addAnActionListener(new AnActionListener.Adapter() {
      @Override
      public void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        if (action instanceof SaveAllAction) {
          // TODO: check if any file stamps are newer than what we last sent over
          // TODO: check documentHasErrors has well

          // com.intellij.ide.actions.SaveAllAction
          final AnAction reloadAction = ActionManager.getInstance().getAction(ReloadFlutterApp.ID);
          if (reloadAction instanceof FlutterAppAction) {
            final FlutterApp app = ((FlutterAppAction)reloadAction).getApp();
            if (app.isStarted()) {
              app.performHotReload(supportsPauseAfterReload());
            }
          }
        }
      }
    }, project);
  }

  public void saveAllAndReload() {
    final FlutterApp app = getApp();
    if (app != null) {
      saveAllAndReload(app);
    }
  }

  public void saveAllAndReload(@NotNull FlutterApp app) {
    if (app.isStarted()) {
      cancelTimer();

      try {
        ignoreCount++;
        FileDocumentManager.getInstance().saveAllDocuments();
      }
      finally {
        ignoreCount--;
      }

      app.performHotReload(supportsPauseAfterReload());
    }
  }

  public void saveAllAndRestart() {
    final FlutterApp app = getApp();
    if (app != null) {
      saveAllAndRestart(app);
    }
  }

  public void saveAllAndRestart(@NotNull FlutterApp app) {
    if (app.isStarted()) {
      cancelTimer();

      try {
        ignoreCount++;
        FileDocumentManager.getInstance().saveAllDocuments();
      }
      finally {
        ignoreCount--;
      }

      app.performRestartApp();
    }
  }

  private FlutterApp getApp() {
    final AnAction action = ActionManager.getInstance().getAction(ReloadFlutterApp.ID);
    return action instanceof FlutterAppAction ? ((FlutterAppAction)action).getApp() : null;
  }

  private boolean supportsPauseAfterReload() {
    return DartPluginCapabilities.isSupported("supports.pausePostRequest");
  }

  //@Override
  //public void beforeAllDocumentsSaving() {
  //  if (ignoreCount > 0) {
  //    return;
  //  }
  //
  //  if (!mySettings.isReloadOnSave()) {
  //    cancelTimer();
  //  }
  //  else {
  //    fileChangedCount = 0;
  //
  //    if (timer != null) {
  //      timer.stop();
  //    }
  //    startTimer();
  //  }
  //}

  //@Override
  //public void beforeDocumentSaving(@NotNull Document document) {
  //  if (ignoreCount > 0) {
  //    return;
  //  }
  //
  //  if (timer == null) {
  //    return;
  //  }
  //
  //  final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
  //  if (file == null || !FlutterUtils.isDartFile(file)) {
  //    return;
  //  }
  //
  //  final Module module = ModuleUtilCore.findModuleForFile(file, myProject);
  //  if (module != null && FlutterModuleUtils.usesFlutter(module)) {
  //    if (documentHasErrors(myProject, document)) {
  //      cancelTimer();
  //    }
  //    else {
  //      fileChangedCount++;
  //    }
  //  }
  //}

  private boolean documentHasErrors(@NotNull Project project, @NotNull Document document) {
    final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    final PsiErrorElement firstError = PsiTreeUtil.findChildOfType(psiFile, PsiErrorElement.class, false);
    return firstError != null;
  }

  //private void startTimer() {
  //  timer = new Timer(10, e -> {
  //    timer = null;
  //
  //    if (fileChangedCount > 0) {
  //      final AnAction action = ActionManager.getInstance().getAction(ReloadFlutterApp.ID);
  //      if (action instanceof FlutterAppAction) {
  //        final FlutterApp app = ((FlutterAppAction)action).getApp();
  //        if (app.isStarted()) {
  //          app.performHotReload(supportsPauseAfterReload());
  //        }
  //      }
  //    }
  //  });
  //  timer.setRepeats(false);
  //  timer.start();
  //}

  private void cancelTimer() {
    if (timer != null) {
      timer.stop();
      timer = null;
    }
  }
}
