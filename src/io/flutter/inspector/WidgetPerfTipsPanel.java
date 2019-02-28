/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.google.common.collect.ArrayListMultimap;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import io.flutter.FlutterInitializer;
import io.flutter.perf.*;
import io.flutter.run.daemon.FlutterApp;
import io.flutter.utils.AsyncUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.util.*;

/**
 * Panel displaying performance tips for the currently visible files.
 */
public class WidgetPerfTipsPanel extends JPanel {
  static final int PERF_TIP_COMPUTE_DELAY = 1000;
  private final FlutterApp app;
  private final FlutterWidgetPerfManager perfManager;

  // Computing performance tips is somewhat expensive so we don't want to
  // compute them too frequently. Performance tips are only computed when
  // new performance stats are available but performance stats are updated
  // at 60fps so to be conservative we delay computing perf tips.
  private final Timer perfTipComputeDelayTimer;
  long lastUpdateTime;
  final private JPanel perfTips;

  /**
   * Currently active file editor if it is a TextEditor.
   */
  private TextEditor currentEditor;
  private ArrayList<TextEditor> currentTextEditors;

  final LinkListener<PerfTip> linkListener;
  private boolean visible = true;
  private List<PerfTip> currentTips;

  public WidgetPerfTipsPanel(Disposable parentDisposable, @NotNull FlutterApp app) {
    this.app = app;

    setLayout(new VerticalLayout(5));

    perfManager = FlutterWidgetPerfManager.getInstance(app.getProject());
    perfTips = new JPanel();
    perfTips.setLayout(new VerticalLayout(0));

    linkListener = (source, tip) -> handleTipSelection(tip);
    final Project project = app.getProject();
    final MessageBusConnection bus = project.getMessageBus().connect(project);
    final FileEditorManagerListener listener = new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        selectedEditorChanged();
      }
    };
    selectedEditorChanged();
    bus.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener);

    perfTipComputeDelayTimer = new Timer(PERF_TIP_COMPUTE_DELAY, this::onComputePerfTips);
    perfTipComputeDelayTimer.start();

    Disposer.register(parentDisposable, perfTipComputeDelayTimer::stop);
  }

  private static void handleTipSelection(@NotNull PerfTip tip) {
    // Send analytics.
    FlutterInitializer.getAnalytics().sendEvent("perf", "perfTipSelected." + tip.getRule().getId());
    BrowserLauncher.getInstance().browse(tip.getUrl(), null);
  }

  private void onComputePerfTips(ActionEvent event) {
    final FlutterWidgetPerf stats = perfManager.getCurrentStats();
    if (stats != null) {
      final long latestPerfUpdate = stats.getLastLocalPerfEventTime();
      // Only do work if new performance stats have been recorded.
      if (latestPerfUpdate != lastUpdateTime) {
        lastUpdateTime = latestPerfUpdate;
        updateTip();
      }
    }
  }

  void hidePerfTip() {
    currentTips = null;
    remove(perfTips);

    setVisible(hasPerfTips());
  }

  private void selectedEditorChanged() {
    lastUpdateTime = -1;
    updateTip();
  }

  private void updateTip() {
    if (perfManager.getCurrentStats() == null) {
      return;
    }
    final Set<TextEditor> selectedEditors = new HashSet<>(perfManager.getSelectedEditors());
    if (selectedEditors.isEmpty()) {
      hidePerfTip();
      return;
    }

    final WidgetPerfLinter linter = perfManager.getCurrentStats().getPerfLinter();
    AsyncUtils.whenCompleteUiThread(linter.getTipsFor(selectedEditors), (tips, throwable) -> {
      if (tips == null || throwable != null || tips.isEmpty()) {
        hidePerfTip();
        return;
      }

      final Map<String, TextEditor> forPath = new HashMap<>();
      for (TextEditor editor : selectedEditors) {
        final VirtualFile file = editor.getFile();
        if (file != null) {
          forPath.put(InspectorService.toSourceLocationUri(file.getPath()), editor);
        }
      }
      final ArrayListMultimap<TextEditor, PerfTip> newTipsForFile = ArrayListMultimap.create();
      for (PerfTip tip : tips) {
        for (Location location : tip.getLocations()) {
          if (forPath.containsKey(location.path)) {
            newTipsForFile.put(forPath.get(location.path), tip);
          }
        }
      }

      final ArrayList<TextEditor> changedEditors = new ArrayList<>();
      for (TextEditor editor : newTipsForFile.keySet()) {
        final List<PerfTip> entry = newTipsForFile.get(editor);
        if (tipsPerFile == null || !PerfTipRule.equivalentPerfTips(entry, tipsPerFile.get(editor))) {
          changedEditors.add(editor);
        }
      }
      tipsPerFile = newTipsForFile;
      if (!PerfTipRule.equivalentPerfTips(currentTips, tips)) {
        showPerfTips(tips);
      }
    });
  }

  ArrayListMultimap<TextEditor, PerfTip> tipsPerFile;

  private void showPerfTips(ArrayList<PerfTip> tips) {
    perfTips.removeAll();
    final PerfTip lastMainTip = currentTips != null && currentTips.size() > 0 ? currentTips.get(0) : null;
    currentTips = tips;
    for (PerfTip tip : tips) {
      final LinkLabel<PerfTip> label = new LinkLabel<>(
        "<html><body><a>" + tip.getMessage() + "</a><body></html>",
        tip.getRule().getIcon(),
        linkListener,
        tip
      );
      label.setPaintUnderline(false);
      label.setBorder(JBUI.Borders.empty(5));
      perfTips.add(label);
    }

    add(perfTips, 0);

    setVisible(hasPerfTips());
  }

  private boolean hasPerfTips() {
    return currentTips != null && !currentTips.isEmpty();
  }

  public void setVisibleToUser(boolean visible) {
    if (visible != this.visible) {
      this.visible = visible;
      if (visible) {
        // Reset last update time to ensure performance tips will be recomputed
        // the next time onComputePerfTips is called.
        lastUpdateTime = -1;
        perfTipComputeDelayTimer.start();
      }
      else {
        perfTipComputeDelayTimer.stop();
      }
    }
  }
}
