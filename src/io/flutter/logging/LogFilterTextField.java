/*
 * Copyright 2018 The Chromium Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
package io.flutter.logging;

import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.SearchTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.DocumentEvent;
import java.awt.event.KeyEvent;

public class LogFilterTextField extends SearchTextField {
  @Nullable
  private OnFilterListener onFilterListener;

  public LogFilterTextField() {
    super(true);
    addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        doFilterIfNeeded();
      }
    });
  }

  public void setOnFilterListener(@Nullable OnFilterListener onFilterListener) {
    this.onFilterListener = onFilterListener;
  }

  @Override
  protected boolean preprocessEventForTextField(KeyEvent e) {
    if ((KeyEvent.VK_ENTER == e.getKeyCode()) || ('\n' == e.getKeyChar())) {
      e.consume();
      addCurrentTextToHistory();
    }
    return super.preprocessEventForTextField(e);
  }

  @Override
  protected void onFocusLost() {
    super.addCurrentTextToHistory();
    doFilterIfNeeded();
  }

  @Override
  protected void onFieldCleared() {
    doFilterIfNeeded();
  }

  private void doFilterIfNeeded() {
    if (onFilterListener != null) {
      onFilterListener.onFilter();
    }
  }

  public interface OnFilterListener {
    void onFilter();
  }
}
