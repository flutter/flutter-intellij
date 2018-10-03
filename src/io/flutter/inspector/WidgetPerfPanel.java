/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.inspector;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import io.flutter.run.daemon.FlutterApp;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

// TODO(jacobr): display a table of all widget perf stats in this panel along
// with the summary information about the current performance stats.

/**
 * Panel displaying basic information on Widget perf for the currently selected
 * file.
 */
public class WidgetPerfPanel extends JPanel {
  static final int PANEL_HEIGHT = 120;
  private final JBLabel perfMessage;

  /**
   * Currently active file editor.
   */
  private FileEditor currentEditor;

  /**
   * Range within the current active file editor to show stats for.
   */
  private TextRange currentRange;

  public WidgetPerfPanel(Disposable parentDisposable, @NotNull FlutterApp app) {
    super(new BorderLayout());

    setPreferredSize(new Dimension(-1, PANEL_HEIGHT));
    setMaximumSize(new Dimension(Short.MAX_VALUE, PANEL_HEIGHT));
    perfMessage = new JBLabel();
    final Box labelBox = Box.createHorizontalBox();
    labelBox.add(perfMessage);
    labelBox.add(Box.createHorizontalGlue());
    labelBox.setBorder(JBUI.Borders.empty(3, 10));
    add(labelBox);

    final Project project = app.getProject();
    final MessageBusConnection bus = project.getMessageBus().connect(project);
    final FileEditorManagerListener listener = new FileEditorManagerListener() {
      @Override
      public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        setSelectedEditor(event.getNewEditor());
      }
    };
    bus.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener);

    Disposer.register(parentDisposable, () -> {
      // TODO(jacobr): unsubscribe?
    });
  }

  private void setSelectedEditor(FileEditor editor) {
    if (editor == currentEditor) {
      return;
    }
    currentRange = null;
    currentEditor = editor;
    perfMessage.setText("");
  }

  public void setPerfMessage(FileEditor editor, TextRange range, String message) {
    setSelectedEditor(editor);
    currentRange = range;
    perfMessage.setText(message);
  }
}
