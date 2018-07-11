/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.ui.PopupMenuListenerAdapter;
import com.intellij.ui.components.JBScrollPane;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.text.DefaultEditorKit;
import java.awt.event.ActionEvent;
import java.util.Optional;

class FlutterLogPopup {
  private static final String GOOGLE_SEARCH_QUERY = "https://www.google.com/search?q=%s";
  private static final String POPUP_TITLE = "Selected log";
  @NotNull
  private final JTextArea logTextArea;
  @NotNull
  private final JBScrollPane logScrollPane;

  public FlutterLogPopup() {
    logTextArea = createLogView();
    logScrollPane = new JBScrollPane(logTextArea);
  }

  void showLogDialog(@NotNull String log) {
    logTextArea.setText(log);
    JOptionPane.showMessageDialog(null, logScrollPane, POPUP_TITLE, JOptionPane.PLAIN_MESSAGE);
  }

  @NotNull
  private JTextArea createLogView() {
    final JTextArea logTextArea = new JTextArea(10, 100);
    logTextArea.setWrapStyleWord(true);
    logTextArea.setLineWrap(true);
    logTextArea.setEditable(false);
    logTextArea.setComponentPopupMenu(createLogPopupMenu());
    return logTextArea;
  }

  @NotNull
  private JPopupMenu createLogPopupMenu() {
    final JPopupMenu menu = new JPopupMenu();

    final Action copy = new DefaultEditorKit.CopyAction();
    copy.putValue(Action.NAME, "Copy");
    getCopyKeyStroke().ifPresent(stroke -> copy.putValue(Action.ACCELERATOR_KEY, stroke));
    menu.add(copy);

    final Action search = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        BrowserUtil.browse(String.format(GOOGLE_SEARCH_QUERY, logTextArea.getSelectedText()));
      }
    };
    search.putValue(Action.NAME, "Search with Google");
    menu.add(search);

    menu.addPopupMenuListener(new PopupMenuListenerAdapter() {
      @Override
      public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        super.popupMenuWillBecomeVisible(e);
        final String selectedLog = logTextArea.getSelectedText();
        final boolean isExistSelectedLog = StringUtils.isNotEmpty(selectedLog);
        copy.setEnabled(isExistSelectedLog);
        search.setEnabled(isExistSelectedLog);
      }
    });
    return menu;
  }

  @NotNull
  private Optional<KeyStroke> getCopyKeyStroke() {
    final AnAction actionCopy = ActionManager.getInstance().getAction(IdeActions.ACTION_COPY);
    for (Shortcut shortcut : actionCopy.getShortcutSet().getShortcuts()) {
      if (shortcut instanceof KeyboardShortcut) {
        return Optional.of(((KeyboardShortcut)shortcut).getFirstKeyStroke());
      }
    }
    return Optional.empty();
  }
}
