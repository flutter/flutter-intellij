/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.coverage;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import io.flutter.FlutterUtils;
import io.flutter.run.FlutterAppManager;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// TODO(devoncarew): Have an opt-out for this feature.

// TODO(devoncarew): Handle the case where we're reloading coverage for a file that has been editing
//       by the user since the last reload (where the source that we're editing has different line
//       positions than what's running on the VM).

/**
 * A singleton for the current Project. This class watches for changes to the current
 * Flutter app, and orchestrates displaying live code coverage for the current file.
 */
public class FlutterLiveCoverageManager implements Disposable {
  public static final boolean ENABLE_LIVE_COVERAGE = false;

  /**
   * Initialize the live coverage manager for the given project.
   */
  public static void init(@NotNull Project project) {
    // Call getInstance() will init FlutterLiveCoverageManager for the given project.
    getInstance(project);
  }

  public static FlutterLiveCoverageManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, FlutterLiveCoverageManager.class);
  }

  private FlutterAppLiveCoverage currentCoverage;
  private VirtualFile lastFile;
  private FileEditor lastEditor;

  private FlutterLiveCoverageManager(@NotNull Project project) {
    Disposer.register(project, this);

    FlutterAppManager.getInstance(project).getActiveAppAsStream().listen(
      this::updateCurrentAppChanged, true);

    final MessageBusConnection connection = project.getMessageBus().connect(project);

    final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor instanceof EditorEx) {
      lastFile = ((EditorEx)editor).getVirtualFile();

      if (couldContainCoverage(lastFile)) {
        lastEditor = FileEditorManager.getInstance(project).getSelectedEditor(lastFile);

        if (lastEditor == null) {
          lastFile = null;
        }
      }
    }

    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        if (couldContainCoverage(event.getNewFile())) {
          lastFile = event.getNewFile();
          lastEditor = editorFor(event);
        }
        else {
          lastFile = null;
          lastEditor = null;
        }

        notifyCoverage();
      }
    });
  }

  private boolean couldContainCoverage(@Nullable VirtualFile file) {
    return file != null && FlutterUtils.isDartFile(file);
  }

  private FileEditor editorFor(FileEditorManagerEvent event) {
    if (!(event.getNewEditor() instanceof TextEditor)) {
      return null;
    }
    return event.getNewEditor();
  }

  private void updateCurrentAppChanged(@Nullable FlutterApp app) {
    if (app == null) {
      if (currentCoverage != null) {
        currentCoverage.dispose();
        currentCoverage = null;
      }
    }
    else if (currentCoverage == null) {
      if (ENABLE_LIVE_COVERAGE && app.getLaunchMode().supportsDebugConnection()) {
        currentCoverage = new FlutterAppLiveCoverage(app);
        notifyCoverage();
      }
    }
    else if (currentCoverage.getApp() != app) {
      currentCoverage.dispose();

      if (ENABLE_LIVE_COVERAGE && app.getLaunchMode().supportsDebugConnection()) {
        currentCoverage = new FlutterAppLiveCoverage(app);
        notifyCoverage();
      }
    }
  }

  private void notifyCoverage() {
    if (currentCoverage == null) {
      return;
    }

    if (lastFile == null) {
      currentCoverage.showFor(null, null);
    }
    else {
      final Module module = currentCoverage.getApp().getModule();

      if (module != null && ModuleUtilCore.moduleContainsFile(module, lastFile, false)) {
        currentCoverage.showFor(lastFile, lastEditor);
      }
      else {
        currentCoverage.showFor(null, null);
      }
    }
  }

  @Override
  public void dispose() {
    if (currentCoverage != null) {
      currentCoverage.dispose();
      currentCoverage = null;
    }
  }
}
