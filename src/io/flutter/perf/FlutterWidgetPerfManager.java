/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

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

// TODO(jacobr): Have an opt-out for this feature.

/**
 * A singleton for the current Project. This class watches for changes to the current
 * Flutter app, and orchestrates displaying rebuild counts and other performance
 * results broken down at the widget level for the current file.
 *
 * Rebuilt counts provide an easy way to understand the coarse grained
 * performance of an application and avoid common pitfalls.
 */
public class FlutterWidgetPerfManager implements Disposable {
  public static final boolean ENABLE_REBUILD_COUNTS = true;

  /**
   * Initialize the rebuild count manager for the given project.
   */
  public static void init(@NotNull Project project) {
    // Call getInstance() will init FlutterWidgetPerfManager for the given project.
    getInstance(project);
  }

  public static FlutterWidgetPerfManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, FlutterWidgetPerfManager.class);
  }

  private FlutterWidgetPerf currentStats;
  private VirtualFile lastFile;
  private FileEditor lastEditor;

  private FlutterWidgetPerfManager(@NotNull Project project) {
    Disposer.register(project, this);

    FlutterAppManager.getInstance(project).getActiveAppAsStream().listen(
      this::updateCurrentAppChanged, true);

    final MessageBusConnection connection = project.getMessageBus().connect(project);

    final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
    if (editor instanceof EditorEx) {
      lastFile = ((EditorEx)editor).getVirtualFile();

      if (couldContainWidgets(lastFile)) {
        lastEditor = FileEditorManager.getInstance(project).getSelectedEditor(lastFile);

        if (lastEditor == null) {
          lastFile = null;
        }
      }
    }

    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        if (couldContainWidgets(event.getNewFile())) {
          lastFile = event.getNewFile();
          lastEditor = editorFor(event);
        }
        else {
          lastFile = null;
          lastEditor = null;
        }

        notifyPerf();
      }
    });
  }

  private boolean couldContainWidgets(@Nullable VirtualFile file) {
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
      if (currentStats != null) {
        currentStats.dispose();
        currentStats = null;
      }
    }
    else if (currentStats == null) {
      if (ENABLE_REBUILD_COUNTS && app.getLaunchMode().supportsDebugConnection()) {
        currentStats = new FlutterWidgetPerf(app);
        notifyPerf();
      }
    }
    else if (currentStats.getApp() != app) {
      currentStats.dispose();

      if (ENABLE_REBUILD_COUNTS && app.getLaunchMode().supportsDebugConnection()) {
        currentStats = new FlutterWidgetPerf(app);
        notifyPerf();
      }
    }
  }

  private void notifyPerf() {
    if (currentStats == null) {
      return;
    }

    if (lastFile == null) {
      currentStats.showFor(null, null);
    }
    else {
      final Module module = currentStats.getApp().getModule();

      if (module != null && ModuleUtilCore.moduleContainsFile(module, lastFile, false)) {
        currentStats.showFor(lastFile, lastEditor);
      }
      else {
        currentStats.showFor(null, null);
      }
    }
  }

  @Override
  public void dispose() {
    if (currentStats != null) {
      currentStats.dispose();
      currentStats = null;
    }
  }
}
