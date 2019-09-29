/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.perf;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import io.flutter.FlutterInitializer;
import io.flutter.FlutterUtils;
import io.flutter.run.FlutterAppManager;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.StreamSubscription;
import io.flutter.view.FlutterViewMessages;
import io.flutter.vmService.ServiceExtensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A singleton for the current Project. This class watches for changes to the
 * current Flutter app, and orchestrates displaying rebuild counts and other
 * widget performance stats for widgets created in the active source files.
 * Performance stats are displayed directly in the TextEditor windows so that
 * users can see them as they look at the source code.
 * <p>
 * Rebuild counts provide an easy way to understand the coarse grained
 * performance of an application and avoid common pitfalls.
 * <p>
 * FlutterWidgetPerfManager tracks which source files are visible and
 * passes that information to FlutterWidgetPerf which performs the work to
 * actually fetch performance information and display them.
 */
public class FlutterWidgetPerfManager implements Disposable, FlutterApp.FlutterAppListener {

  // Whether each of the performance metrics tracked should be tracked by
  // default when starting a new application.
  public static boolean trackRebuildWidgetsDefault = true; // XXX hack
  public static boolean trackRepaintWidgetsDefault = false;

  public FlutterWidgetPerf getCurrentStats() {
    return currentStats;
  }

  private FlutterWidgetPerf currentStats;
  private FlutterApp app;
  private final Project project;
  private boolean trackRebuildWidgets = trackRebuildWidgetsDefault;
  private boolean trackRepaintWidgets = trackRepaintWidgetsDefault;
  private boolean debugIsActive;

  private final Set<PerfModel> listeners = new HashSet<>();

  /**
   * File editors visible to the user that might contain widgets.
   */
  private Set<TextEditor> lastSelectedEditors = new HashSet<>();

  private final List<StreamSubscription<Boolean>> streamSubscriptions = new ArrayList<>();


  @NotNull
  public Set<TextEditor> getSelectedEditors() {
    return lastSelectedEditors;
  }

  private FlutterWidgetPerfManager(@NotNull Project project) {
    this.project = project;

    Disposer.register(project, this);

    FlutterAppManager.getInstance(project).getActiveAppAsStream().listen(
      this::updateCurrentAppChanged, true);

    project.getMessageBus().connect().subscribe(
      FlutterViewMessages.FLUTTER_DEBUG_TOPIC, (event) -> debugActive(project, event)
    );

    final MessageBusConnection connection = project.getMessageBus().connect(project);

    updateSelectedEditors();
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        if (updateSelectedEditors()) {
          notifyPerf();
        }
      }
    });
  }

  /**
   * @return whether the set of selected editors actually changed.
   */
  private boolean updateSelectedEditors() {
    final FileEditor[] editors = FileEditorManager.getInstance(project).getSelectedEditors();
    final Set<TextEditor> newEditors = new HashSet<>();
    for (FileEditor editor : editors) {
      if (editor instanceof TextEditor) {
        final VirtualFile file = editor.getFile();
        if (FlutterUtils.couldContainWidgets(file)) {
          newEditors.add((TextEditor)editor);
        }
      }
    }
    if (newEditors.equals(lastSelectedEditors)) {
      return false;
    }
    lastSelectedEditors = newEditors;
    return true;
  }

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

  public boolean isTrackRebuildWidgets() {
    return trackRebuildWidgets;
  }

  public void setTrackRebuildWidgets(boolean value) {
    if (value == trackRebuildWidgets) {
      return;
    }
    trackRebuildWidgets = value;
    onProfilingFlagsChanged();
    if (debugIsActive && app != null && app.isSessionActive()) {
      updateTrackWidgetRebuilds();
    }
    // Send analytics.
    if (trackRebuildWidgets) {
      FlutterInitializer.getAnalytics().sendEvent("intellij", "TrackWidgetRebuilds");
    }
  }

  public boolean isTrackRepaintWidgets() {
    return trackRepaintWidgets;
  }

  public void setTrackRepaintWidgets(boolean value) {
    if (value == trackRepaintWidgets) {
      return;
    }
    trackRepaintWidgets = value;
    onProfilingFlagsChanged();
    if (debugIsActive && app != null && app.isSessionActive()) {
      updateTrackWidgetRepaints();
    }
    // Send analytics.
    if (trackRepaintWidgets) {
      FlutterInitializer.getAnalytics().sendEvent("intellij", "TrackRepaintWidgets");
    }
  }

  private void onProfilingFlagsChanged() {
    if (currentStats != null) {
      currentStats.setProfilingEnabled(isProfilingEnabled());
    }
  }

  private boolean isProfilingEnabled() {
    return trackRebuildWidgets || trackRepaintWidgets;
  }

  private void debugActive(Project project, FlutterViewMessages.FlutterDebugEvent event) {
    debugIsActive = true;

    if (app == null) {
      return;
    }

    app.addStateListener(this);
    syncBooleanServiceExtension(ServiceExtensions.trackRebuildWidgets.getExtension(), () -> trackRebuildWidgets);
    syncBooleanServiceExtension(ServiceExtensions.trackRepaintWidgets.getExtension(), () -> trackRepaintWidgets);

    currentStats = new FlutterWidgetPerf(
      isProfilingEnabled(),
      new VmServiceWidgetPerfProvider(app),
      (TextEditor textEditor) -> new EditorPerfDecorations(textEditor, app),
      path -> new DocumentFileLocationMapper(path, app.getProject())
    );

    for (PerfModel listener : listeners) {
      currentStats.addPerfListener(listener);
    }
  }

  public void stateChanged(FlutterApp.State newState) {
    switch (newState) {
      case RELOADING:
        if (currentStats != null) currentStats.clear();
        break;
      case RESTARTING:
        if (currentStats != null) currentStats.onRestart();
        break;
      case STARTED:
        notifyPerf();
        break;
    }
  }

  public void notifyAppRestarted() {
    currentStats.clear();
  }

  public void notifyAppReloaded() {
    currentStats.clear();
  }

  private void updateTrackWidgetRebuilds() {
    app.maybeCallBooleanExtension(ServiceExtensions.trackRebuildWidgets.getExtension(), trackRebuildWidgets)
      .whenCompleteAsync((v, e) -> notifyPerf());
  }

  private void updateTrackWidgetRepaints() {
    app.maybeCallBooleanExtension(ServiceExtensions.trackRepaintWidgets.getExtension(), trackRepaintWidgets)
      .whenCompleteAsync((v, e) -> notifyPerf());
  }

  private void syncBooleanServiceExtension(String serviceExtension, Computable<Boolean> valueProvider) {
    StreamSubscription<Boolean> subscription = app.hasServiceExtension(serviceExtension, (supported) -> {
      if (supported) {
        app.callBooleanExtension(serviceExtension, valueProvider.compute());
      }
    });
    if (subscription != null) {
      streamSubscriptions.add(subscription);
    }
  }

  private void updateCurrentAppChanged(@Nullable FlutterApp app) {
    // TODO(jacobr): we currently only support showing stats for the last app
    // that was run. After the initial CL lands we should fix this to track
    // multiple running apps if needed. The most important use case is if the
    // user has one profile app and one debug app running at the same time.
    // We should track stats for all running apps and display the aggregated
    // stats. A well behaved flutter app should not be painting frames very
    // frequently when a user is not interacting with it so showing aggregated
    // stats for all apps should match user expectations without forcing users
    // to manage which app they have selected.
    if (app == this.app) {
      return;
    }
    debugIsActive = false;

    if (this.app != null) {
      this.app.removeStateListener(this);
    }

    this.app = app;

    for (StreamSubscription<Boolean> subscription : streamSubscriptions) {
      subscription.dispose();
    }
    streamSubscriptions.clear();

    if (currentStats != null) {
      currentStats.dispose();
      currentStats = null;
    }
  }

  private void notifyPerf() {
    if (!trackRepaintWidgets && !trackRebuildWidgets && currentStats != null) {
      // TODO(jacobr): consider just marking as idle.
      currentStats.clear();
    }

    if (currentStats == null) {
      return;
    }

    if (lastSelectedEditors.isEmpty()) {
      currentStats.showFor(lastSelectedEditors);
      return;
    }
    final Module module = app.getModule();
    final Set<TextEditor> editors = new HashSet<>();
    if (module != null) {
      for (TextEditor editor : lastSelectedEditors) {
        final VirtualFile file = editor.getFile();
        if (file != null &&
            ModuleUtilCore.moduleContainsFile(module, file, false) &&
            !app.isReloading() || !app.isLatestVersionRunning(file)) {
          // We skip querying files that have been modified locally as we
          // cannot safely display the profile information so there is no
          // point in tracking it.
          editors.add(editor);
        }
      }
    }
    currentStats.showFor(editors);
  }

  @Override
  public void dispose() {
    if (currentStats != null) {
      currentStats.dispose();
      currentStats = null;
      listeners.clear();
    }
  }

  public void addPerfListener(PerfModel listener) {
    listeners.add(listener);
    if (currentStats != null) {
      currentStats.addPerfListener(listener);
    }
  }

  public void removePerfListener(PerfModel listener) {
    listeners.remove(listener);
    if (currentStats != null) {
      currentStats.removePerfListener(listener);
    }
  }
}
