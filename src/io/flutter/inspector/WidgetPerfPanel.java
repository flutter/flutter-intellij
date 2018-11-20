/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import io.flutter.FlutterInitializer;
import io.flutter.perf.FlutterWidgetPerf;
import io.flutter.perf.FlutterWidgetPerfManager;
import io.flutter.perf.PerfTip;
import io.flutter.perf.WidgetPerfLinter;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;

// TODO(jacobr): display a table of all widget perf stats in this panel along
// with the summary information about the current performance stats.

/**
 * Panel displaying basic information on Widget perf for the currently selected
 * file.
 */
public class WidgetPerfPanel extends JPanel {
  static final int PERF_TIP_COMPUTE_DELAY = 1000;
  private final JBLabel perfMessage;
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

  /**
   * Range within the current active file editor to show stats for.
   */
  private TextRange currentRange;

  LinkListener<PerfTip> linkListener;
  private boolean visible = true;

  public WidgetPerfPanel(Disposable parentDisposable, @NotNull FlutterApp app) {
    setLayout(new VerticalLayout(5));
    this.app = app;
    perfManager = FlutterWidgetPerfManager.getInstance(app.getProject());

    perfMessage = new JBLabel();
    final Box labelBox = Box.createHorizontalBox();
    labelBox.add(perfMessage);
    labelBox.add(Box.createHorizontalGlue());
    labelBox.setBorder(JBUI.Borders.empty(3, 10));
    add(labelBox);

    perfTips = new JPanel();
    perfTips.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Perf Tips"));
    perfTips.setLayout(new VerticalLayout(0));

    linkListener = (source, tip) -> handleTipSelection(tip);
    final Project project = app.getProject();
    final MessageBusConnection bus = project.getMessageBus().connect(project);
    final FileEditorManagerListener listener = new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        setSelectedEditor(event.getNewEditor());
      }
    };
    final FileEditor[] selectedEditors = FileEditorManager.getInstance(project).getSelectedEditors();
    if (selectedEditors.length > 0) {
      setSelectedEditor(selectedEditors[0]);
    }
    bus.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener);

    perfTipComputeDelayTimer = new Timer(PERF_TIP_COMPUTE_DELAY, this::onComputePerfTips);
    perfTipComputeDelayTimer.start();

    // TODO(jacobr): unsubscribe?
    Disposer.register(parentDisposable, perfTipComputeDelayTimer::stop);
  }

  private static void handleTipSelection(@NotNull PerfTip tip) {
    // Send analytics.
    FlutterInitializer.getAnalytics().sendEvent("perf", "perfTipSelected." + tip.getRule().getAnalyticsId());
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
    remove(perfTips);
  }

  private void setSelectedEditor(FileEditor editor) {
    lastUpdateTime = -1;
    if (!(editor instanceof TextEditor)) {
      editor = null;
    }
    if (editor == currentEditor) {
      return;
    }
    currentRange = null;
    currentEditor = (TextEditor)editor;
    perfMessage.setText("");

    updateTip();
  }

  private void updateTip() {
    if (perfManager.getCurrentStats() == null) {
      return;
    }
    if (currentEditor == null) {
      hidePerfTip();
      return;
    }

    final WidgetPerfLinter linter = perfManager.getCurrentStats().getPerfLinter();
    linter.getTipsFor(perfManager.getSelectedEditors()).whenCompleteAsync((tips, throwable) -> {
      if (tips == null || throwable != null || tips.isEmpty()) {
        hidePerfTip();
        return;
      }
      showPerfTips(tips);
    });
  }

  private void showPerfTips(ArrayList<PerfTip> tips) {
    perfTips.removeAll();
    for (PerfTip tip : tips) {
      final LinkLabel<PerfTip> label = new LinkLabel<>(tip.getMessage(), tip.getRule().getIcon(), linkListener, tip);
      label.setBorder(JBUI.Borders.empty(5));
      perfTips.add(label);
    }

    add(perfTips, 0);
  }

  public void setPerfStatusMessage(FileEditor editor, TextRange range, String message) {
    setSelectedEditor(editor);
    currentRange = range;
    perfMessage.setText(message);
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
